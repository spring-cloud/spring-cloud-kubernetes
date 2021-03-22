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

	private static final String HTTPS_PORT_NAME = "https";

	private static final String HTTP_PORT_NAME = "http";

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

		return ep.getSubsets().stream().filter(subset -> subset.getPorts() != null && subset.getPorts().size() > 0) // safeguard
				.flatMap(subset -> {
					Map<String, String> metadata = new HashMap<>(svcMetadata);
					List<V1EndpointPort> endpointPorts = subset.getPorts();
					if (this.properties.getMetadata() != null && this.properties.getMetadata().isAddPorts()) {
						endpointPorts.forEach(p -> metadata.put(p.getName(), Integer.toString(p.getPort())));
					}
					List<V1EndpointAddress> addresses = subset.getAddresses();
					if (addresses == null) {
						addresses = new ArrayList<>();
					}
					if (this.properties.isIncludeNotReadyAddresses()
							&& !CollectionUtils.isEmpty(subset.getNotReadyAddresses())) {
						addresses.addAll(subset.getNotReadyAddresses());
					}

					final int port = findEndpointPort(endpointPorts, primaryPortName, serviceId);
					return addresses.stream()
							.map(addr -> new KubernetesServiceInstance(
									addr.getTargetRef() != null ? addr.getTargetRef().getUid() : "", serviceId,
									addr.getIp(), port, metadata, false));
				}).collect(Collectors.toList());
	}

	private int findEndpointPort(List<V1EndpointPort> endpointPorts, String primaryPortName, String serviceId) {
		if (endpointPorts.size() == 1) {
			return endpointPorts.get(0).getPort();
		}
		else {
			Map<String, Integer> ports = endpointPorts.stream().filter(p -> StringUtils.hasText(p.getName()))
					.collect(Collectors.toMap(V1EndpointPort::getName, V1EndpointPort::getPort));
			// This oneliner is looking for a port with a name equal to the primary port
			// name specified in the service label
			// or in spring.cloud.kubernetes.discovery.primary-port-name, equal to https,
			// or equal to http.
			// In case no port has been found return -1 to log a warning and fall back to
			// the first port in the list.
			int discoveredPort = ports.getOrDefault(primaryPortName,
					ports.getOrDefault(HTTPS_PORT_NAME, ports.getOrDefault(HTTP_PORT_NAME, -1)));

			if (discoveredPort == -1) {
				if (StringUtils.hasText(primaryPortName)) {
					log.warn("Could not find a port named '" + primaryPortName + "', 'https', or 'http' for service '"
							+ serviceId + "'.");
				}
				else {
					log.warn("Could not find a port named 'https' or 'http' for service '" + serviceId + "'.");
				}
				log.warn(
						"Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
				log.warn("Alternatively name the primary port 'https' or 'http'");
				log.warn("An incorrect configuration may result in non-deterministic behaviour.");
				discoveredPort = endpointPorts.get(0).getPort();
			}
			return discoveredPort;
		}
	}

	@Override
	public List<String> getServices() {
		List<V1Service> services = this.properties.isAllNamespaces() ? this.serviceLister.list()
				: this.serviceLister.namespace(this.namespace).list();
		return services.stream().filter(s -> s.getMetadata() != null) // safeguard
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
