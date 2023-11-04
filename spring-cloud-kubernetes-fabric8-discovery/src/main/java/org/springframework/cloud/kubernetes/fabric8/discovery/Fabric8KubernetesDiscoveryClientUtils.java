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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterNested;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import jakarta.annotation.Nullable;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author wind57
 */
final class Fabric8KubernetesDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8KubernetesDiscoveryClientUtils.class));

	static final Predicate<Service> ALWAYS_TRUE = x -> true;

	private Fabric8KubernetesDiscoveryClientUtils() {

	}

	static List<Endpoints> endpoints(KubernetesDiscoveryProperties properties, KubernetesClient client,
			KubernetesNamespaceProvider namespaceProvider, String target, @Nullable String serviceName,
			Predicate<Service> filter) {

		List<Endpoints> endpoints;

		if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in namespaces : " + properties.namespaces());
			List<Endpoints> inner = new ArrayList<>(properties.namespaces().size());
			properties.namespaces().forEach(namespace -> inner.addAll(filteredEndpoints(
					client.endpoints().inNamespace(namespace).withNewFilter(), properties, serviceName)));
			endpoints = inner;
		}
		else if (properties.allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			endpoints = filteredEndpoints(client.endpoints().inAnyNamespace().withNewFilter(), properties, serviceName);
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

		if (properties.filter() == null || properties.filter().isBlank() || filter == ALWAYS_TRUE) {
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

	/**
	 * a service is allowed to have a single port defined without a name.
	 */
	static Map<String, Integer> endpointSubsetsPortData(List<EndpointSubset> endpointSubsets) {
		return endpointSubsets.stream().flatMap(endpointSubset -> endpointSubset.getPorts().stream())
				.collect(Collectors.toMap(
						endpointPort -> hasText(endpointPort.getName()) ? endpointPort.getName() : UNSET_PORT_NAME,
						EndpointPort::getPort));
	}

	static ServiceMetadata serviceMetadata(Service service) {
		ObjectMeta metadata = service.getMetadata();
		ServiceSpec serviceSpec = service.getSpec();
		return new ServiceMetadata(metadata.getName(), metadata.getNamespace(), serviceSpec.getType(),
				metadata.getLabels(), metadata.getAnnotations());
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
