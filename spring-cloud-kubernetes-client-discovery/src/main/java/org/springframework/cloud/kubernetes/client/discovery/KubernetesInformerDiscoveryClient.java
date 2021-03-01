/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.client.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kubernetes.client.extended.wait.Wait;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Min Kim
 * @author Ryan Baxter
 * @author Tim Yysewyn
 */
public class KubernetesInformerDiscoveryClient implements DiscoveryClient, InitializingBean {

	private static final Log log = LogFactory.getLog(KubernetesInformerDiscoveryClient.class);
	private static final String PRIMARY_PORT_NAME_LABEL_KEY = "primary-port-name";

	private final SharedInformerFactory sharedInformerFactory;

	private final Lister<V1Service> serviceLister;

	private final Supplier<Boolean> informersReadyFunc;

	private final Lister<V1Endpoints> endpointsLister;

	private final KubernetesDiscoveryProperties properties;

	private final String namespace;

	public KubernetesInformerDiscoveryClient(String namespace, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		this.namespace = namespace;
		this.sharedInformerFactory = sharedInformerFactory;

		this.serviceLister = serviceLister;
		this.endpointsLister = endpointsLister;
		this.informersReadyFunc = () -> serviceInformer.hasSynced() && endpointsInformer.hasSynced();

		this.properties = properties;
	}

	@Override
	public String description() {
		return "Kubernetes Client Discovery";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId, "[Assertion failed] - the object argument must not be null");

		if (!StringUtils.hasText(namespace) && !properties.isAllNamespaces()) {
			log.warn("Namespace is null or empty, this may cause issues looking up services");
		}

		V1Service service = properties.isAllNamespaces() ? this.serviceLister.list().stream()
				.filter(svc -> serviceId.equals(svc.getMetadata().getName())).findFirst().orElse(null)
				: this.serviceLister.namespace(this.namespace).get(serviceId);
		if (service == null) {
			// no such service present in the cluster
			return new ArrayList<>();
		}

		Map<String, String> svcMetadata = new HashMap<>();
		if (this.properties.getMetadata() != null) {
			if (this.properties.getMetadata().isAddLabels()) {
				if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
					String labelPrefix = this.properties.getMetadata().getLabelsPrefix() != null
							? this.properties.getMetadata().getLabelsPrefix() : "";
					service.getMetadata().getLabels().entrySet().stream()
							.filter(e -> e.getKey().startsWith(labelPrefix))
							.forEach(e -> svcMetadata.put(e.getKey(), e.getValue()));
				}
			}
			if (this.properties.getMetadata().isAddAnnotations()) {
				if (service.getMetadata() != null && service.getMetadata().getAnnotations() != null) {
					String annotationPrefix = this.properties.getMetadata().getAnnotationsPrefix() != null
							? this.properties.getMetadata().getAnnotationsPrefix() : "";
					service.getMetadata().getAnnotations().entrySet().stream()
							.filter(e -> e.getKey().startsWith(annotationPrefix))
							.forEach(e -> svcMetadata.put(e.getKey(), e.getValue()));
				}
			}
		}

		V1Endpoints ep = this.endpointsLister.namespace(service.getMetadata().getNamespace())
				.get(service.getMetadata().getName());
		if (ep == null || ep.getSubsets() == null) {
			// no available endpoints in the cluster
			return new ArrayList<>();
		}

		Optional<String> discoveredPrimaryPortName = Optional.empty();
		if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
			discoveredPrimaryPortName = Optional
				.ofNullable(service.getMetadata().getLabels().get(PRIMARY_PORT_NAME_LABEL_KEY));
		}
		final String primaryPortName = discoveredPrimaryPortName.orElse(this.properties.getPrimaryPortName());

		return ep.getSubsets().stream()
			.filter(subset -> subset.getPorts() != null && subset.getPorts().size() > 0) // safeguard
			.flatMap(subset -> {
				Map<String, String> metadata = new HashMap<>(svcMetadata);
				if (this.properties.getMetadata() != null && this.properties.getMetadata().isAddPorts()) {
					subset.getPorts().forEach(p -> metadata.put(p.getName(), Integer.toString(p.getPort())));
				}
				V1EndpointPort port;
				if (subset.getPorts().size() == 1) {
					port = subset.getPorts().get(0);
				} else {
					if (primaryPortName == null) {
						log.warn("Could not decide which port to use for service '" + serviceId + "'.");
						log.warn("Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
						return Stream.empty();
					}
					Optional<V1EndpointPort> discoveredPort = subset.getPorts().stream()
						.filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(primaryPortName)).findFirst();
					if (!discoveredPort.isPresent()) {
						log.warn("Could not find a port named '" + primaryPortName + "' for service '" + serviceId + "'.");
						log.warn("Make sure that either the primary-port-name label spring.cloud.kubernetes.discovery.primary-port-name has been configured correctly.");
						return Stream.empty();
					}
					port = discoveredPort.get();
				}
				List<V1EndpointAddress> addresses = subset.getAddresses();
				if (addresses == null) {
					addresses = new ArrayList<>();
				}
				if (this.properties.isIncludeNotReadyAddresses()
						&& !CollectionUtils.isEmpty(subset.getNotReadyAddresses())) {
					addresses.addAll(subset.getNotReadyAddresses());
				}

				return addresses.stream()
						.map(addr -> new KubernetesServiceInstance(
								addr.getTargetRef() != null ? addr.getTargetRef().getUid() : "", serviceId, addr.getIp(),
								port.getPort(), metadata, false));
		}).collect(Collectors.toList());
	}

	@Override
	public List<String> getServices() {
		List<V1Service> services = this.properties.isAllNamespaces() ? this.serviceLister.list()
				: this.serviceLister.namespace(this.namespace).list();
		return services.stream()
			.filter(s -> s.getMetadata() != null) //safeguard
			.map(s -> s.getMetadata().getName()).collect(Collectors.toList());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.sharedInformerFactory.startAllRegisteredInformers();
		if (!Wait.poll(Duration.ofSeconds(1), Duration.ofSeconds(this.properties.getCacheLoadingTimeoutSeconds()),
				() -> {
					log.info("Waiting for the cache of informers to be fully loaded..");
					return this.informersReadyFunc.get();
				})) {
			if (this.properties.isWaitCacheReady()) {
				throw new IllegalStateException(
						"Timeout waiting for informers cache to be ready, is the kubernetes service up?");
			}
			else {
				log.warn(
						"Timeout waiting for informers cache to be ready, ignoring the failure because waitForInformerCacheReady property is false");
			}
		}
		log.info("Cache fully loaded (total " + serviceLister.list().size()
				+ " services) , discovery client is now available");
	}

}
