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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kubernetes.client.extended.wait.Wait;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;

import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.matchesServiceLabels;

/**
 * @author Min Kim
 * @author Ryan Baxter
 * @author Tim Yysewyn
 */
public class KubernetesInformerDiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesInformerDiscoveryClient.class));

	private static final String PRIMARY_PORT_NAME_LABEL_KEY = "primary-port-name";

	private static final String SECURED_KEY = "secured";

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
		return "Fabric8 Kubernetes Client Discovery";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must be provided");

		if (!StringUtils.hasText(namespace) && !properties.allNamespaces()) {
			LOG.warn(() -> "Namespace is null or empty, this may cause issues looking up services");
		}

		List<V1Service> services = properties.allNamespaces() ? serviceLister.list().stream()
				.filter(svc -> serviceId.equals(svc.getMetadata().getName())).toList()
				: List.of(serviceLister.namespace(namespace).get(serviceId));
		if (services.size() == 0 || !services.stream().anyMatch(service -> matchesServiceLabels(service, properties))) {
			// no such service present in the cluster
			return new ArrayList<>();
		}
		return services.stream().flatMap(s -> getServiceInstanceDetails(s, serviceId)).toList();
	}

	private Stream<ServiceInstance> getServiceInstanceDetails(V1Service service, String serviceId) {
		Map<String, String> svcMetadata = new HashMap<>();
		if (properties.metadata() != null) {
			if (properties.metadata().addLabels()) {
				if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
					String labelPrefix = properties.metadata().labelsPrefix() != null
							? properties.metadata().labelsPrefix() : "";
					service.getMetadata().getLabels().entrySet().stream()
							.filter(e -> e.getKey().startsWith(labelPrefix))
							.forEach(e -> svcMetadata.put(e.getKey(), e.getValue()));
				}
			}
			if (properties.metadata().addAnnotations()) {
				if (service.getMetadata() != null && service.getMetadata().getAnnotations() != null) {
					String annotationPrefix = properties.metadata().annotationsPrefix() != null
							? properties.metadata().annotationsPrefix() : "";
					service.getMetadata().getAnnotations().entrySet().stream()
							.filter(e -> e.getKey().startsWith(annotationPrefix))
							.forEach(e -> svcMetadata.put(e.getKey(), e.getValue()));
				}
			}
		}

		V1Endpoints ep = endpointsLister.namespace(service.getMetadata().getNamespace())
				.get(service.getMetadata().getName());
		if (ep == null || ep.getSubsets() == null) {
			// no available endpoints in the cluster
			return Stream.empty();
		}

		Optional<String> discoveredPrimaryPortName = Optional.empty();
		if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
			discoveredPrimaryPortName = Optional
					.ofNullable(service.getMetadata().getLabels().get(PRIMARY_PORT_NAME_LABEL_KEY));
		}
		final String primaryPortName = discoveredPrimaryPortName.orElse(properties.primaryPortName());

		final boolean secured = isSecured(service);

		return ep.getSubsets().stream().filter(subset -> subset.getPorts() != null && subset.getPorts().size() > 0) // safeguard
				.flatMap(subset -> {
					Map<String, String> metadata = new HashMap<>(svcMetadata);
					List<CoreV1EndpointPort> endpointPorts = subset.getPorts();
					if (properties.metadata() != null && properties.metadata().addPorts()) {
						endpointPorts.forEach(
								p -> metadata.put(StringUtils.hasText(p.getName()) ? p.getName() : UNSET_PORT_NAME,
										Integer.toString(p.getPort())));
					}
					List<V1EndpointAddress> addresses = subset.getAddresses();
					if (addresses == null) {
						addresses = new ArrayList<>();
					}
					if (properties.includeNotReadyAddresses()
							&& !CollectionUtils.isEmpty(subset.getNotReadyAddresses())) {
						addresses.addAll(subset.getNotReadyAddresses());
					}

					final int port = findEndpointPort(endpointPorts, primaryPortName, serviceId);
					return addresses.stream()
							.map(addr -> new DefaultKubernetesServiceInstance(
									addr.getTargetRef() != null ? addr.getTargetRef().getUid() : "", serviceId,
									addr.getIp(), port, metadata, secured, service.getMetadata().getNamespace(),
									// TODO find out how to get cluster name possibly from
									// KubeConfig
									null));
				});
	}

	private static boolean isSecured(V1Service service) {
		Optional<String> securedOpt = Optional.empty();
		if (service.getMetadata() != null && service.getMetadata().getAnnotations() != null) {
			securedOpt = Optional.ofNullable(service.getMetadata().getAnnotations().get(SECURED_KEY));
		}
		if (!securedOpt.isPresent() && service.getMetadata() != null && service.getMetadata().getLabels() != null) {
			securedOpt = Optional.ofNullable(service.getMetadata().getLabels().get(SECURED_KEY));
		}
		return Boolean.parseBoolean(securedOpt.orElse("false"));
	}

	private int findEndpointPort(List<CoreV1EndpointPort> endpointPorts, String primaryPortName, String serviceId) {
		if (endpointPorts.size() == 1) {
			return endpointPorts.get(0).getPort();
		}
		else {
			Map<String, Integer> ports = endpointPorts.stream().filter(p -> StringUtils.hasText(p.getName()))
					.collect(Collectors.toMap(CoreV1EndpointPort::getName, CoreV1EndpointPort::getPort));
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
					LOG.warn(() -> "Could not find a port named '" + primaryPortName + "', 'https', or 'http' for service '"
							+ serviceId + "'.");
				}
				else {
					LOG.warn(() -> "Could not find a port named 'https' or 'http' for service '" + serviceId + "'.");
				}
				LOG.warn(() ->
						"Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
				LOG.warn(() -> "Alternatively name the primary port 'https' or 'http'");
				LOG.warn(() -> "An incorrect configuration may result in non-deterministic behaviour.");
				discoveredPort = endpointPorts.get(0).getPort();
			}
			return discoveredPort;
		}
	}

	@Override
	public List<String> getServices() {
		List<V1Service> services = properties.allNamespaces() ? serviceLister.list()
				: serviceLister.namespace(namespace).list();
		return services.stream().filter(service -> matchesServiceLabels(service, properties)).map(s -> s.getMetadata().getName())
				.collect(Collectors.toList());
	}

	@PostConstruct
	public void afterPropertiesSet() {
		sharedInformerFactory.startAllRegisteredInformers();
		if (!Wait.poll(Duration.ofSeconds(1), Duration.ofSeconds(properties.cacheLoadingTimeoutSeconds()), () -> {
			LOG.info(() -> "Waiting for the cache of informers to be fully loaded..");
			return informersReadyFunc.get();
		})) {
			if (properties.waitCacheReady()) {
				throw new IllegalStateException(
						"Timeout waiting for informers cache to be ready, is the kubernetes service up?");
			}
			else {
				LOG.warn(
					() -> "Timeout waiting for informers cache to be ready, ignoring the failure because waitForInformerCacheReady property is false");
			}
		}
		LOG.info(() -> "Cache fully loaded (total " + serviceLister.list().size()
				+ " services) , discovery client is now available");
	}

}
