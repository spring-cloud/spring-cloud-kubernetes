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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.filter;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.matchesServiceLabels;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.postConstruct;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.serviceMetadata;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SECURED;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;

/**
 * @author Min Kim
 * @author Ryan Baxter
 * @author Tim Yysewyn
 */
public class KubernetesInformerDiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesInformerDiscoveryClient.class));

	private final List<SharedInformerFactory> sharedInformerFactories;

	private final List<Lister<V1Service>> serviceListers;

	private final List<Lister<V1Endpoints>> endpointsListers;

	private final Supplier<Boolean> informersReadyFunc;

	private final KubernetesDiscoveryProperties properties;

	private final Predicate<V1Service> filter;

	@Deprecated(forRemoval = true)
	public KubernetesInformerDiscoveryClient(String namespace, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = List.of(sharedInformerFactory);
		this.serviceListers = List.of(serviceLister);
		this.endpointsListers = List.of(endpointsLister);
		this.informersReadyFunc = () -> serviceInformer.hasSynced() && endpointsInformer.hasSynced();
		this.properties = properties;
		filter = filter(properties);
	}

	public KubernetesInformerDiscoveryClient(SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = List.of(sharedInformerFactory);
		this.serviceListers = List.of(serviceLister);
		this.endpointsListers = List.of(endpointsLister);
		this.informersReadyFunc = () -> serviceInformer.hasSynced() && endpointsInformer.hasSynced();
		this.properties = properties;
		filter = filter(properties);
	}

	public KubernetesInformerDiscoveryClient(List<SharedInformerFactory> sharedInformerFactories,
			List<Lister<V1Service>> serviceListers, List<Lister<V1Endpoints>> endpointsListers,
			List<SharedInformer<V1Service>> serviceInformers, List<SharedInformer<V1Endpoints>> endpointsInformers,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = sharedInformerFactories;

		this.serviceListers = serviceListers;
		this.endpointsListers = endpointsListers;
		this.informersReadyFunc = () -> {
			boolean serviceInformersReady = serviceInformers.isEmpty() || serviceInformers.stream()
					.map(SharedInformer::hasSynced).reduce(Boolean::logicalAnd).orElse(false);
			boolean endpointsInformersReady = endpointsInformers.isEmpty() || endpointsInformers.stream()
					.map(SharedInformer::hasSynced).reduce(Boolean::logicalAnd).orElse(false);
			return serviceInformersReady && endpointsInformersReady;
		};

		this.properties = properties;
		filter = filter(properties);
	}

	@Override
	public String description() {
		return "Kubernetes Client Discovery";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must be provided");

		List<V1Service> services = serviceListers.stream().flatMap(x -> x.list().stream())
				.filter(scv -> scv.getMetadata() != null).filter(svc -> serviceId.equals(svc.getMetadata().getName()))
				.filter(scv -> matchesServiceLabels(scv, properties)).filter(filter).toList();
		return services.stream().flatMap(service -> getServiceInstanceDetails(service, serviceId)).toList();
	}

	private Stream<ServiceInstance> getServiceInstanceDetails(V1Service service, String serviceId) {
		Map<String, String> serviceMetadata = serviceMetadata(properties, service, serviceId);

		List<V1Endpoints> endpoints = endpointsListers.stream()
				.map(endpointsLister -> endpointsLister.namespace(service.getMetadata().getNamespace())
						.get(service.getMetadata().getName()))
				.filter(Objects::nonNull).filter(ep -> ep.getSubsets() != null).toList();

		Optional<String> discoveredPrimaryPortName = Optional.empty();
		if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
			discoveredPrimaryPortName = Optional
					.ofNullable(service.getMetadata().getLabels().get(PRIMARY_PORT_NAME_LABEL_KEY));
		}
		final String primaryPortName = discoveredPrimaryPortName.orElse(properties.primaryPortName());

		final boolean secured = isSecured(service);

		return endpoints.stream()
				.flatMap(ep -> ep.getSubsets().stream()
						.filter(subset -> subset.getPorts() != null && subset.getPorts().size() > 0) // safeguard
						.flatMap(subset -> {
							Map<String, String> metadata = new HashMap<>(serviceMetadata);
							List<CoreV1EndpointPort> endpointPorts = subset.getPorts();
							if (properties.metadata() != null && properties.metadata().addPorts()) {
								endpointPorts.forEach(p -> metadata.put(
										StringUtils.hasText(p.getName()) ? p.getName() : UNSET_PORT_NAME,
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
											// TODO find out how to get cluster name
											// possibly from
											// KubeConfig
											null));
						}));
	}

	private static boolean isSecured(V1Service service) {
		Optional<String> securedOpt = Optional.empty();
		if (service.getMetadata() != null && service.getMetadata().getAnnotations() != null) {
			securedOpt = Optional.ofNullable(service.getMetadata().getAnnotations().get(SECURED));
		}
		if (!securedOpt.isPresent() && service.getMetadata() != null && service.getMetadata().getLabels() != null) {
			securedOpt = Optional.ofNullable(service.getMetadata().getLabels().get(SECURED));
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
					LOG.warn(() -> "Could not find a port named '" + primaryPortName
							+ "', 'https', or 'http' for service '" + serviceId + "'.");
				}
				else {
					LOG.warn(() -> "Could not find a port named 'https' or 'http' for service '" + serviceId + "'.");
				}
				LOG.warn(
						() -> "Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
				LOG.warn(() -> "Alternatively name the primary port 'https' or 'http'");
				LOG.warn(() -> "An incorrect configuration may result in non-deterministic behaviour.");
				discoveredPort = endpointPorts.get(0).getPort();
			}
			return discoveredPort;
		}
	}

	@Override
	public List<String> getServices() {
		List<String> services = serviceListers.stream().flatMap(serviceLister -> serviceLister.list().stream())
				.filter(service -> matchesServiceLabels(service, properties)).filter(filter)
				.map(s -> s.getMetadata().getName()).distinct().toList();
		LOG.debug(() -> "will return services : " + services);
		return services;
	}

	@PostConstruct
	public void afterPropertiesSet() {
		postConstruct(sharedInformerFactories, properties, informersReadyFunc, serviceListers);
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

}
