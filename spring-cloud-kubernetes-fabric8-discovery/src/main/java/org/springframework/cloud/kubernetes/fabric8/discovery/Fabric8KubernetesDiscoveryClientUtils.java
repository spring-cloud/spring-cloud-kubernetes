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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceList;
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
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.keysWithPrefix;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SERVICE_TYPE;

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

	static int endpointsPort(EndpointSubset endpointSubset, String serviceId, KubernetesDiscoveryProperties properties,
			Service service) {

		List<EndpointPort> endpointPorts = endpointSubset.getPorts();
		if (endpointPorts.size() == 1) {
			int port = endpointPorts.get(0).getPort();
			LOG.debug(() -> "endpoint ports has a single entry, using port : " + port);
			return port;
		}

		else {

			Optional<Integer> port;
			String primaryPortName = primaryPortName(properties, service, serviceId);

			Map<String, Integer> existingPorts = endpointPorts.stream()
					.filter(endpointPort -> StringUtils.hasText(endpointPort.getName()))
					.collect(Collectors.toMap(EndpointPort::getName, EndpointPort::getPort));

			port = fromMap(existingPorts, primaryPortName, "found primary-port-name (with value: '" + primaryPortName
					+ "') via properties or service labels to match port");
			if (port.isPresent()) {
				return port.get();
			}

			port = fromMap(existingPorts, HTTPS, "found primary-port-name via 'https' to match port");
			if (port.isPresent()) {
				return port.get();
			}

			port = fromMap(existingPorts, HTTP, "found primary-port-name via 'http' to match port");
			if (port.isPresent()) {
				return port.get();
			}

			logWarnings();
			return endpointPorts.get(0).getPort();

		}
	}

	/**
	 * take primary-port-name from service label "PRIMARY_PORT_NAME_LABEL_KEY" if it
	 * exists, otherwise from KubernetesDiscoveryProperties if it exists, otherwise null.
	 */
	static String primaryPortName(KubernetesDiscoveryProperties properties, Service service, String serviceId) {
		String primaryPortNameFromProperties = properties.primaryPortName();
		Map<String, String> serviceLabels = service.getMetadata().getLabels();

		// the value from labels takes precedence over the one from properties
		String primaryPortName = Optional
				.ofNullable(Optional.ofNullable(serviceLabels).orElse(Map.of()).get(PRIMARY_PORT_NAME_LABEL_KEY))
				.orElse(primaryPortNameFromProperties);

		if (primaryPortName == null) {
			LOG.debug(
					() -> "did not find a primary-port-name in neither properties nor service labels for service with ID : "
							+ serviceId);
			return null;
		}

		LOG.debug(() -> "will use primaryPortName : " + primaryPortName + " for service with ID = " + serviceId);
		return primaryPortName;
	}

	/**
	 * labels, annotations, ports metadata and namespace metadata.
	 */
	static Map<String, String> serviceMetadata(String serviceId, Service service,
			KubernetesDiscoveryProperties properties, List<EndpointSubset> endpointSubsets, String namespace) {
		Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = properties.metadata();
		if (metadataProps.addLabels()) {
			Map<String, String> labelMetadata = keysWithPrefix(service.getMetadata().getLabels(),
					metadataProps.labelsPrefix());
			LOG.debug(() -> "Adding labels metadata: " + labelMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.addAnnotations()) {
			Map<String, String> annotationMetadata = keysWithPrefix(service.getMetadata().getAnnotations(),
					metadataProps.annotationsPrefix());
			LOG.debug(() -> "Adding annotations metadata: " + annotationMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(annotationMetadata);
		}

		if (metadataProps.addPorts()) {
			Map<String, String> ports = endpointSubsets.stream()
					.flatMap(endpointSubset -> endpointSubset.getPorts().stream())
					.filter(port -> StringUtils.hasText(port.getName()))
					.collect(toMap(EndpointPort::getName, port -> Integer.toString(port.getPort())));
			Map<String, String> portMetadata = keysWithPrefix(ports, properties.metadata().portsPrefix());
			if (!portMetadata.isEmpty()) {
				LOG.debug(() -> "Adding port metadata: " + portMetadata + " for serviceId : " + serviceId);
			}
			serviceMetadata.putAll(portMetadata);
		}

		serviceMetadata.put(NAMESPACE_METADATA_KEY, namespace);
		serviceMetadata.put(SERVICE_TYPE, service.getSpec().getType());
		return serviceMetadata;
	}

	static List<EndpointSlice> endpointSlices(KubernetesDiscoveryProperties properties, KubernetesClient client,
			KubernetesNamespaceProvider namespaceProvider, String target) {

		List<EndpointSlice> endpointSlices;

		if (properties.allNamespaces()) {
			LOG.debug(() -> "discovering endpoint slices in all namespaces");
			endpointSlices = filteredEndpointSlices(
					client.discovery().v1().endpointSlices().inAnyNamespace().withNewFilter(), properties);
		}
		else if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoint slices in namespaces : " + properties.namespaces());
			List<EndpointSlice> inner = new ArrayList<>(properties.namespaces().size());
			properties.namespaces()
					.forEach(namespace -> inner.addAll(filteredEndpointSlices(
							client.discovery().v1().endpointSlices().inNamespace(namespace).withNewFilter(),
							properties)));
			endpointSlices = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, target, namespaceProvider);
			LOG.debug(() -> "discovering endpoint slices in namespace : " + namespace);
			endpointSlices = filteredEndpointSlices(
					client.discovery().v1().endpointSlices().inNamespace(namespace).withNewFilter(), properties);
		}

		return endpointSlices;
	}

	static List<Endpoints> endpoints(KubernetesDiscoveryProperties properties, KubernetesClient client,
			KubernetesNamespaceProvider namespaceProvider, String target, @Nullable String serviceName) {

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

		return endpoints;
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
			Service service, @Nullable EndpointAddress endpointAddress, int endpointPort, String serviceId,
			Map<String, String> serviceMetadata, String namespace) {
		// instanceId is usually the pod-uid as seen in the .metadata.uid
		String instanceId = Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
				.map(ObjectReference::getUid).orElseGet(() -> service.getMetadata().getUid());

		boolean secured;
		if (servicePortSecureResolver == null) {
			secured = false;
		}
		else {
			secured = servicePortSecureResolver
					.resolve(new ServicePortSecureResolver.Input(endpointPort, service.getMetadata().getName(),
							service.getMetadata().getLabels(), service.getMetadata().getAnnotations()));
		}

		String host = Optional.ofNullable(endpointAddress).map(EndpointAddress::getIp)
				.orElseGet(() -> service.getSpec().getExternalName());

		return new DefaultKubernetesServiceInstance(instanceId, serviceId, host, endpointPort, serviceMetadata, secured,
				namespace, null);
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

	private static List<EndpointSlice> filteredEndpointSlices(
			FilterNested<FilterWatchListDeletable<EndpointSlice, EndpointSliceList, Resource<EndpointSlice>>> filterNested,
			KubernetesDiscoveryProperties properties) {

		FilterNested<FilterWatchListDeletable<EndpointSlice, EndpointSliceList, Resource<EndpointSlice>>> partial = filterNested
				.withLabels(properties.serviceLabels());

		return partial.endFilter().list().getItems();

	}

	private static Optional<Integer> fromMap(Map<String, Integer> existingPorts, String key, String message) {
		Integer fromPrimaryPortName = existingPorts.get(key);
		if (fromPrimaryPortName == null) {
			LOG.debug(() -> "not " + message);
			return Optional.empty();
		}
		else {
			LOG.debug(() -> message + " : " + fromPrimaryPortName);
			return Optional.of(fromPrimaryPortName);
		}
	}

	private static void logWarnings() {
		LOG.warn(() -> """
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour.""");
	}

}
