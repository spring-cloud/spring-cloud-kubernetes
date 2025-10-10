/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.endpointsPort;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstance;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.externalNameServiceInstance;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstanceMetadata;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.serviceMetadata;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.addresses;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.endpointSubsetsPortData;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.endpoints;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.services;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8InstanceIdHostPodNameSupplier.externalName;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8InstanceIdHostPodNameSupplier.nonExternalName;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8PodLabelsAndAnnotationsSupplier.externalName;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8PodLabelsAndAnnotationsSupplier.nonExternalName;

/**
 * Fabric8 Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
final class Fabric8DiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8DiscoveryClient.class));

	private final KubernetesDiscoveryProperties properties;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final KubernetesClient client;

	private final KubernetesNamespaceProvider namespaceProvider;

	private final Predicate<Service> predicate;

	Fabric8DiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			ServicePortSecureResolver servicePortSecureResolver, KubernetesNamespaceProvider namespaceProvider,
			Predicate<Service> predicate) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
		this.servicePortSecureResolver = servicePortSecureResolver;
		this.namespaceProvider = namespaceProvider;
		this.predicate = predicate;
	}

	@Override
	public String description() {
		return "Fabric8 Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId);

		List<Endpoints> allEndpoints = endpoints(properties, client, namespaceProvider, "fabric8-discovery", serviceId,
				predicate);

		List<ServiceInstance> instances = new ArrayList<>();
		for (Endpoints endpoints : allEndpoints) {
			// endpoints are only those that matched the serviceId
			instances.addAll(serviceInstances(endpoints, serviceId));
		}

		if (properties.includeExternalNameServices()) {
			LOG.debug(() -> "Searching for 'ExternalName' type of services with serviceId : " + serviceId);
			List<Service> services = services(properties, client, namespaceProvider,
					s -> s.getSpec().getType().equals(EXTERNAL_NAME), Map.of("metadata.name", serviceId),
					"fabric8-discovery");
			for (Service service : services) {
				ServiceMetadata serviceMetadata = serviceMetadata(service);
				Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(Map.of(), serviceMetadata,
						properties);

				Fabric8InstanceIdHostPodNameSupplier supplierOne = externalName(service);

				ServiceInstance externalNameServiceInstance = externalNameServiceInstance(
					serviceMetadata, supplierOne, serviceInstanceMetadata
				);

				instances.add(externalNameServiceInstance);
			}
		}

		return instances;
	}

	@Override
	public List<String> getServices() {
		List<String> services = services(properties, client, namespaceProvider, predicate, null, "fabric8 discovery")
			.stream()
			.map(service -> service.getMetadata().getName())
			.distinct()
			.toList();
		LOG.debug(() -> "will return services : " + services);
		return services;
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

	private List<ServiceInstance> serviceInstances(Endpoints endpoints, String serviceId) {

		List<EndpointSubset> subsets = endpoints.getSubsets();
		if (subsets.isEmpty()) {
			LOG.debug(() -> "serviceId : " + serviceId + " does not have any subsets");
			return List.of();
		}

		String namespace = endpoints.getMetadata().getNamespace();
		List<ServiceInstance> instances = new ArrayList<>();

		Service service = client.services().inNamespace(namespace).withName(serviceId).get();
		ServiceMetadata serviceMetadata = serviceMetadata(service);
		Map<String, Integer> portsData = endpointSubsetsPortData(subsets);

		Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(portsData, serviceMetadata, properties);

		for (EndpointSubset endpointSubset : subsets) {

			Map<String, Integer> endpointsPortData = endpointSubsetsPortData(List.of(endpointSubset));
			ServicePortNameAndNumber portData = endpointsPort(endpointsPortData, serviceMetadata, properties);

			List<EndpointAddress> addresses = addresses(endpointSubset, properties);
			for (EndpointAddress endpointAddress : addresses) {

				Fabric8InstanceIdHostPodNameSupplier supplierOne = nonExternalName(endpointAddress, service);
				Fabric8PodLabelsAndAnnotationsSupplier supplierTwo = nonExternalName(client, namespace);

				ServiceInstance serviceInstance = serviceInstance(servicePortSecureResolver, serviceMetadata,
						supplierOne, supplierTwo, portData, serviceInstanceMetadata, properties);
				instances.add(serviceInstance);
			}
		}

		return instances;
	}

}
