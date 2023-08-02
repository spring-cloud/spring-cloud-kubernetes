/*
 * Copyright 2013-2023 the original author or authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterNested;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import jakarta.annotation.Nullable;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SERVICE_TYPE;
import static org.springframework.cloud.kubernetes.fabric8.discovery.ServicePortSecureResolver.Input;

/**
 * @author wind57
 */
final class Fabric8KubernetesDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8KubernetesDiscoveryClientUtils.class));

	private Fabric8KubernetesDiscoveryClientUtils() {

	}

	static EndpointSubsetNS subsetsFromEndpoints(Endpoints endpoints) {
		return new EndpointSubsetNS(endpoints.getMetadata().getNamespace(), endpoints.getSubsets());
	}

	static List<Endpoints> endpoints(KubernetesDiscoveryProperties properties, KubernetesClient client,
			KubernetesNamespaceProvider namespaceProvider, String target, @Nullable String serviceName,
			Predicate<Service> filter) {

		List<Endpoints> endpoints;

		if (properties.allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			endpoints = filteredEndpoints(client.endpoints().inAnyNamespace().withNewFilter(), properties, serviceName);
		}
		else if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in namespaces : " + properties.namespaces());
			List<Endpoints> inner = new ArrayList<>(properties.namespaces().size());
			properties.namespaces().forEach(namespace -> inner.addAll(filteredEndpoints(
					client.endpoints().inNamespace(namespace).withNewFilter(), properties, serviceName)));
			endpoints = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, target, namespaceProvider);
			LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
			endpoints = filteredEndpoints(client.endpoints().inNamespace(namespace).withNewFilter(), properties,
					serviceName);
		}

		return withFilter(endpoints, properties, client, filter);
	}

	// see https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1182 on why this
	// is needed
	static List<Endpoints> withFilter(List<Endpoints> endpoints, KubernetesDiscoveryProperties properties,
			KubernetesClient client, Predicate<Service> filter) {

		if (properties.filter() == null || properties.filter().isBlank()) {
			LOG.debug(() -> "filter not present");
			return endpoints;
		}

		List<Endpoints> result = new ArrayList<>();
		// group by namespace in order to make a single API call per namespace when
		// retrieving services
		Map<String, List<Endpoints>> endpointsByNamespace = endpoints.stream()
				.collect(Collectors.groupingBy(x -> x.getMetadata().getNamespace()));

		for (Map.Entry<String, List<Endpoints>> entry : endpointsByNamespace.entrySet()) {
			// get all services in the namespace that match the filter
			Set<String> filteredServiceNames = client.services().inNamespace(entry.getKey()).list().getItems().stream()
					.filter(filter).map(service -> service.getMetadata().getName()).collect(Collectors.toSet());

			// in the previous step we might have taken "too many" services, so in the
			// next one take only those that have a matching endpoints, by name.
			// This way we only get the endpoints that have a matching service with an
			// applied filter, it's like we filtered endpoints by that filter.
			result.addAll(entry.getValue().stream()
					.filter(endpoint -> filteredServiceNames.contains(endpoint.getMetadata().getName())).toList());

		}

		return result;
	}

	/**
	 * serviceName can be null, in which case the filter for "metadata.name" will not be
	 * applied.
	 */
	static List<Endpoints> filteredEndpoints(
			FilterNested<FilterWatchListDeletable<Endpoints, EndpointsList, Resource<Endpoints>>> filterNested,
			KubernetesDiscoveryProperties properties, @Nullable String serviceName) {

		FilterNested<FilterWatchListDeletable<Endpoints, EndpointsList, Resource<Endpoints>>> partial = filterNested
				.withLabels(properties.serviceLabels());

		if (serviceName != null) {
			partial = partial.withField("metadata.name", serviceName);
		}

		return partial.endFilter().list().getItems();

	}

	static List<EndpointAddress> addresses(EndpointSubset endpointSubset, KubernetesDiscoveryProperties properties) {
		List<EndpointAddress> addresses = Optional.ofNullable(endpointSubset.getAddresses()).map(ArrayList::new)
				.orElse(new ArrayList<>());

		if (properties.includeNotReadyAddresses()) {
			List<EndpointAddress> notReadyAddresses = endpointSubset.getNotReadyAddresses();
			if (CollectionUtils.isEmpty(notReadyAddresses)) {
				return addresses;
			}
			addresses.addAll(notReadyAddresses);
		}

		return addresses;
	}

	static ServiceInstance serviceInstance(@Nullable ServicePortSecureResolver servicePortSecureResolver,
			Service service, @Nullable EndpointAddress endpointAddress, ServicePortNameAndNumber portData,
			String serviceId, Map<String, String> serviceMetadata, String namespace,
			KubernetesDiscoveryProperties properties, KubernetesClient client) {
		// instanceId is usually the pod-uid as seen in the .metadata.uid
		String instanceId = Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
				.map(ObjectReference::getUid).orElseGet(() -> service.getMetadata().getUid());

		boolean secured;
		if (servicePortSecureResolver == null) {
			secured = false;
		}
		else {
			secured = servicePortSecureResolver.resolve(new Input(portData, service.getMetadata().getName(),
					service.getMetadata().getLabels(), service.getMetadata().getAnnotations()));
		}

		String host = Optional.ofNullable(endpointAddress).map(EndpointAddress::getIp)
				.orElseGet(() -> service.getSpec().getExternalName());

		Map<String, Map<String, String>> podMetadata = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);

		return new DefaultKubernetesServiceInstance(instanceId, serviceId, host, portData.portNumber(), serviceMetadata,
				secured, namespace, null, podMetadata);
	}

	static List<Service> services(KubernetesDiscoveryProperties properties, KubernetesClient client,
			KubernetesNamespaceProvider namespaceProvider, Predicate<Service> predicate,
			Map<String, String> fieldFilters, String target) {

		List<Service> services;

		if (properties.allNamespaces()) {
			LOG.debug(() -> "discovering services in all namespaces");
			services = filteredServices(client.services().inAnyNamespace().withNewFilter(), properties, predicate,
					fieldFilters);
		}
		else if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "discovering services in namespaces : " + properties.namespaces());
			List<Service> inner = new ArrayList<>(properties.namespaces().size());
			properties.namespaces().forEach(
					namespace -> inner.addAll(filteredServices(client.services().inNamespace(namespace).withNewFilter(),
							properties, predicate, fieldFilters)));
			services = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, target, namespaceProvider);
			LOG.debug(() -> "discovering services in namespace : " + namespace);
			services = filteredServices(client.services().inNamespace(namespace).withNewFilter(), properties, predicate,
					fieldFilters);
		}

		return services;
	}

	static Map<String, Map<String, String>> podMetadata(KubernetesClient client, Map<String, String> serviceMetadata,
			KubernetesDiscoveryProperties properties, EndpointAddress endpointAddress, String namespace) {
		if (!EXTERNAL_NAME.equals(serviceMetadata.get(SERVICE_TYPE))) {
			if (properties.metadata().addPodLabels() || properties.metadata().addPodAnnotations()) {
				String podName = Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
						.filter(objectReference -> "Pod".equals(objectReference.getKind()))
						.map(ObjectReference::getName).orElse(null);

				if (podName != null) {
					ObjectMeta metadata = Optional
							.ofNullable(client.pods().inNamespace(namespace).withName(podName).get())
							.map(Pod::getMetadata).orElse(new ObjectMeta());
					Map<String, Map<String, String>> result = new HashMap<>();
					if (properties.metadata().addPodLabels() && !metadata.getLabels().isEmpty()) {
						result.put("labels", metadata.getLabels());
					}

					if (properties.metadata().addPodAnnotations() && !metadata.getAnnotations().isEmpty()) {
						result.put("annotations", metadata.getAnnotations());
					}

					LOG.debug(() -> "adding podMetadata : " + result + " from pod : " + podName);
					return result;
				}

			}
		}

		return Map.of();
	}

	static Map<String, String> portsData(List<EndpointSubset> endpointSubsets) {
		return endpointSubsets.stream().flatMap(endpointSubset -> endpointSubset.getPorts().stream())
				.filter(port -> StringUtils.hasText(port.getName()))
				.collect(Collectors.toMap(EndpointPort::getName, port -> Integer.toString(port.getPort())));
	}

	static Map<String, Integer> endpointSubsetPortsData(EndpointSubset endpointSubset) {
		return endpointSubset.getPorts().stream().filter(endpointPort -> StringUtils.hasText(endpointPort.getName()))
				.collect(Collectors.toMap(EndpointPort::getName, EndpointPort::getPort));
	}

	/**
	 * serviceName can be null, in which case, such a filter will not be applied.
	 */
	private static List<Service> filteredServices(
			FilterNested<FilterWatchListDeletable<Service, ServiceList, ServiceResource<Service>>> filterNested,
			KubernetesDiscoveryProperties properties, Predicate<Service> predicate,
			@Nullable Map<String, String> fieldFilters) {

		FilterNested<FilterWatchListDeletable<Service, ServiceList, ServiceResource<Service>>> partial = filterNested
				.withLabels(properties.serviceLabels());

		if (fieldFilters != null) {
			partial = partial.withFields(fieldFilters);
		}

		return partial.endFilter().list().getItems().stream().filter(predicate).toList();

	}

}
