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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.endpointsPort;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.externalNameServiceInstance;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstance;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstanceMetadata;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.serviceMetadata;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.addresses;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.endpointSubsetsPortData;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.postConstruct;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8InstanceIdHostPodNameSupplier.fabric8InstanceIdHostPodNameSupplier;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8PodLabelsAndAnnotationsSupplier.fabric8PodLabelsAndAnnotationsSupplier;

/**
 * @author wind57
 */
abstract class Fabric8AbstractBlockingDiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8AbstractBlockingDiscoveryClient.class));

	private final List<Lister<Service>> serviceListers;

	private final List<Lister<Endpoints>> endpointsListers;

	private final List<SharedIndexInformer<Service>> serviceInformers;

	private final List<SharedIndexInformer<Endpoints>> endpointsInformers;

	private final Supplier<Boolean> informersReadyFunc;

	private final KubernetesDiscoveryProperties properties;

	private final Predicate<Service> predicate;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final KubernetesClient kubernetesClient;

	Fabric8AbstractBlockingDiscoveryClient(KubernetesClient kubernetesClient, List<Lister<Service>> serviceListers,
			List<Lister<Endpoints>> endpointsListers, List<SharedIndexInformer<Service>> serviceInformers,
			List<SharedIndexInformer<Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties,
			Predicate<Service> predicate) {

		this.serviceListers = serviceListers;
		this.endpointsListers = endpointsListers;
		this.serviceInformers = serviceInformers;
		this.endpointsInformers = endpointsInformers;
		this.properties = properties;
		this.predicate = predicate;
		this.kubernetesClient = kubernetesClient;

		servicePortSecureResolver = new ServicePortSecureResolver(properties);

		this.informersReadyFunc = () -> {
			boolean serviceInformersReady = serviceInformers.isEmpty() || serviceInformers.stream()
				.map(SharedIndexInformer::hasSynced)
				.reduce(Boolean::logicalAnd)
				.orElse(false);
			boolean endpointsInformersReady = endpointsInformers.isEmpty() || endpointsInformers.stream()
				.map(SharedIndexInformer::hasSynced)
				.reduce(Boolean::logicalAnd)
				.orElse(false);
			return serviceInformersReady && endpointsInformersReady;
		};
	}

	public abstract String description();

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must be provided");

		List<Service> allServices = serviceListers.stream()
			.flatMap(x -> x.list().stream())
			.filter(service -> service.getMetadata() != null)
			.filter(service -> serviceId.equals(service.getMetadata().getName()))
			.toList();

		List<ServiceInstance> serviceInstances = allServices.stream()
			.filter(predicate)
			.flatMap(service -> serviceInstances(service).stream())
			.collect(Collectors.toCollection(ArrayList::new));

		if (properties.includeExternalNameServices()) {
			LOG.debug(() -> "Searching for 'ExternalName' type of services with serviceId : " + serviceId);
			List<Service> externalNameServices = allServices.stream()
				.filter(s -> s.getSpec() != null)
				.filter(s -> EXTERNAL_NAME.equals(s.getSpec().getType()))
				.toList();
			for (Service service : externalNameServices) {
				ServiceMetadata serviceMetadata = serviceMetadata(service);
				Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(Map.of(), serviceMetadata,
						properties);

				Fabric8InstanceIdHostPodNameSupplier fabric8InstanceIdHostPodNameSupplier = fabric8InstanceIdHostPodNameSupplier(
						service);
				ServiceInstance externalNameServiceInstance = externalNameServiceInstance(serviceMetadata,
						fabric8InstanceIdHostPodNameSupplier, serviceInstanceMetadata);
				serviceInstances.add(externalNameServiceInstance);
			}
		}

		return serviceInstances;
	}

	@Override
	public List<String> getServices() {
		List<String> services = serviceListers.stream()
			.flatMap(serviceLister -> serviceLister.list().stream())
			.filter(predicate)
			.map(s -> s.getMetadata().getName())
			.distinct()
			.toList();
		LOG.debug(() -> "will return services : " + services);
		return services;
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

	@PostConstruct
	void afterPropertiesSet() {
		postConstruct(properties, informersReadyFunc, serviceListers);
	}

	@PreDestroy
	void preDestroy() {
		serviceInformers.forEach(SharedIndexInformer::close);
		endpointsInformers.forEach(SharedIndexInformer::close);
	}

	private List<ServiceInstance> serviceInstances(Service service) {

		String serviceId = service.getMetadata().getName();
		String serviceNamespace = service.getMetadata().getNamespace();

		List<ServiceInstance> instances = new ArrayList<>();

		List<Endpoints> allEndpoints = endpointsListers.stream()
			.map(endpointsLister -> endpointsLister.namespace(serviceNamespace).get(serviceId))
			.filter(Objects::nonNull)
			.toList();

		ServiceMetadata k8sServiceMetadata = serviceMetadata(service);

		for (Endpoints endpoints : allEndpoints) {
			List<EndpointSubset> subsets = endpoints.getSubsets();
			if (subsets == null || subsets.isEmpty()) {
				LOG.debug(() -> "serviceId : " + serviceId + " does not have any subsets");
			}
			else {
				Map<String, Integer> portsData = endpointSubsetsPortData(subsets);
				Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(portsData, k8sServiceMetadata,
						properties);

				for (EndpointSubset endpointSubset : subsets) {

					Map<String, Integer> endpointsPortData = endpointSubsetsPortData(List.of(endpointSubset));
					ServicePortNameAndNumber portData = endpointsPort(endpointsPortData, k8sServiceMetadata,
							properties);

					List<EndpointAddress> addresses = addresses(endpointSubset, properties);
					for (EndpointAddress endpointAddress : addresses) {

						Fabric8InstanceIdHostPodNameSupplier instanceIdHostPodNameSupplier = fabric8InstanceIdHostPodNameSupplier(
								endpointAddress, service);
						Fabric8PodLabelsAndAnnotationsSupplier podLabelsAndAnnotationsSupplier = fabric8PodLabelsAndAnnotationsSupplier(
								kubernetesClient, service.getMetadata().getNamespace());

						ServiceInstance serviceInstance = serviceInstance(servicePortSecureResolver, k8sServiceMetadata,
								instanceIdHostPodNameSupplier, podLabelsAndAnnotationsSupplier, portData,
								serviceInstanceMetadata, properties);
						instances.add(serviceInstance);
					}
				}

			}
		}

		return instances;
	}

}
