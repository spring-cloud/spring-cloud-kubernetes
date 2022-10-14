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
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;

/**
 * @author Min Kim
 * @author Ryan Baxter
 * @author Tim Yysewyn
 */
public class KubernetesInformerDiscoveryClient implements DiscoveryClient, InitializingBean {

	private static final Log log = LogFactory.getLog(KubernetesInformerDiscoveryClient.class);

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

		if (!StringUtils.hasText(namespace) && !properties.allNamespaces()) {
			log.warn("Namespace is null or empty, this may cause issues looking up services");
		}

		V1Service service = properties.allNamespaces() ? this.serviceLister.list().stream()
				.filter(svc -> serviceId.equals(svc.getMetadata().getName())).findFirst().orElse(null)
				: this.serviceLister.namespace(this.namespace).get(serviceId);
		if (service == null || !matchServiceLabels(service)) {
			// no such service present in the cluster
			return new ArrayList<>();
		}

		Map<String, String> svcMetadata = new HashMap<>();
		if (this.properties.metadata() != null) {
			if (this.properties.metadata().addLabels()) {
				if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
					String labelPrefix = this.properties.metadata().labelsPrefix() != null
							? this.properties.metadata().labelsPrefix() : "";
					service.getMetadata().getLabels().entrySet().stream()
							.filter(e -> e.getKey().startsWith(labelPrefix))
							.forEach(e -> svcMetadata.put(e.getKey(), e.getValue()));
				}
			}
			if (this.properties.metadata().addAnnotations()) {
				if (service.getMetadata() != null && service.getMetadata().getAnnotations() != null) {
					String annotationPrefix = this.properties.metadata().annotationsPrefix() != null
							? this.properties.metadata().annotationsPrefix() : "";
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
		final String primaryPortName = discoveredPrimaryPortName.orElse(this.properties.primaryPortName());

		return ep.getSubsets().stream().filter(subset -> subset.getPorts() != null && subset.getPorts().size() > 0) // safeguard
				.flatMap(subset -> {
					Map<String, String> metadata = new HashMap<>(svcMetadata);
					List<V1EndpointPort> endpointPorts = subset.getPorts();
					if (this.properties.metadata() != null && this.properties.metadata().addPorts()) {
						endpointPorts.forEach(
								p -> metadata.put(StringUtils.hasText(p.getName()) ? p.getName() : UNSET_PORT_NAME,
										Integer.toString(p.getPort())));
					}
					List<V1EndpointAddress> addresses = subset.getAddresses();
					if (addresses == null) {
						addresses = new ArrayList<>();
					}
					if (this.properties.includeNotReadyAddresses()
							&& !CollectionUtils.isEmpty(subset.getNotReadyAddresses())) {
						addresses.addAll(subset.getNotReadyAddresses());
					}

					final int port = findEndpointPort(endpointPorts, primaryPortName, serviceId);
					return addresses.stream()
							.map(addr -> new DefaultKubernetesServiceInstance(
									addr.getTargetRef() != null ? addr.getTargetRef().getUid() : "", serviceId,
									addr.getIp(), port, metadata, false, service.getMetadata().getNamespace(),
									service.getMetadata().getClusterName()));
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
					ports.getOrDefault(HTTPS, ports.getOrDefault(HTTP, -1)));

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
		List<V1Service> services = this.properties.allNamespaces() ? this.serviceLister.list()
				: this.serviceLister.namespace(this.namespace).list();
		return services.stream().filter(this::matchServiceLabels).map(s -> s.getMetadata().getName())
				.collect(Collectors.toList());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.sharedInformerFactory.startAllRegisteredInformers();
		if (!Wait.poll(Duration.ofSeconds(1), Duration.ofSeconds(this.properties.cacheLoadingTimeoutSeconds()), () -> {
			log.info("Waiting for the cache of informers to be fully loaded..");
			return this.informersReadyFunc.get();
		})) {
			if (this.properties.waitCacheReady()) {
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

	private boolean matchServiceLabels(V1Service service) {
		if (log.isDebugEnabled()) {
			log.debug("Kubernetes Service Label Properties:");
			if (this.properties.serviceLabels() != null) {
				this.properties.serviceLabels().forEach((key, value) -> log.debug(key + ":" + value));
			}
			log.debug("Service " + service.getMetadata().getName() + " labels:");
			if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
				service.getMetadata().getLabels().forEach((key, value) -> log.debug(key + ":" + value));
			}
		}
		// safeguard
		if (service.getMetadata() == null) {
			return false;
		}
		if (properties.serviceLabels() == null || properties.serviceLabels().isEmpty()) {
			return true;
		}
		return properties.serviceLabels().keySet().stream()
				.allMatch(k -> service.getMetadata().getLabels() != null
						&& service.getMetadata().getLabels().containsKey(k)
						&& service.getMetadata().getLabels().get(k).equals(properties.serviceLabels().get(k)));
	}

}
