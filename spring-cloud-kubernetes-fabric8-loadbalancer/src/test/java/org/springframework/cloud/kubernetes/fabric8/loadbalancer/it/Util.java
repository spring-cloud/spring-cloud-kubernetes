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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.EndpointsListBuilder;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.util.Strings;

import static java.util.stream.Collectors.toList;

/**
 * @author wind57
 */
public final class Util {

	private Util() {

	}

	/**
	 * mock indexer calls that are made when services are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerServiceCall(String namespace, String serviceId,
			KubernetesMockServer kubernetesMockServer) {
		Service service = new ServiceBuilder().withSpec(
				new ServiceSpecBuilder().withType("ClusterIP").withPorts(getServicePorts(Map.of(8080, "a"))).build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		ServiceList emptyServiceList = new ServiceListBuilder().build();

		// first call to populate shared index informer
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/services?resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
					+ "/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock indexer calls that are made when services are requested in a certain
	 * namespace.
	 */
	public static void mockAllNamespacesIndexerServiceCalls(Map<String, String> namespaceToServiceId,
			KubernetesMockServer kubernetesMockServer) {

		List<Service> services = new ArrayList<>();
		for (Map.Entry<String, String> entry : namespaceToServiceId.entrySet()) {
			Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
					.withPorts(getServicePorts(Map.of(8080, "a")))
					.build())
				.withNewMetadata()
				.withNamespace(entry.getKey())
				.withName(entry.getValue())
				.withLabels(Map.of())
				.withAnnotations(Map.of())
				.endMetadata()
				.build();

			services.add(service);
		}

		ServiceList serviceList = new ServiceListBuilder().withItems(services)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		ServiceList emptyServiceList = new ServiceListBuilder().build();

		// first call to populate shared index informer
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	public static void mockLoadBalancerServiceCall(String namespace, String serviceId,
			KubernetesMockServer kubernetesMockServer, int portNumber, String portName, int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(portNumber, portName)))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();

		String urlPath = "/api/v1/namespaces/" + namespace + "/services?fieldSelector=metadata.name%3D" + serviceId;

		// mock the list supplier
		kubernetesMockServer.expect().get().withPath(urlPath).andReturn(200, serviceList).times(numberOfCalls);
	}

	/**
	 * when "metadata.name" is requested for all namespaces
	 */
	public static void mockLoadBalancerMetadataNameServiceCallInAllNamespaces(String namespace, String serviceId,
			KubernetesMockServer kubernetesMockServer, int portNumber, String portName, int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(portNumber, portName)))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();

		// mock the list supplier
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?fieldSelector=metadata.name%3D" + serviceId)
			.andReturn(200, serviceList)
			.times(numberOfCalls);
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerEndpointsCall(String namespace, String serviceId, String host, int port,
			KubernetesMockServer kubernetesMockServer) {

		Endpoints endpoints = new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withName(serviceId).withNamespace(namespace).build())
			.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(port).build())
				.withAddresses(new EndpointAddressBuilder().withIp(host).build())
				.build())
			.build();

		EndpointsList endpointsList = new EndpointsListBuilder().withItems(endpoints)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/endpoints?resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
					+ "/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockAllNamespacesIndexerEndpointsCalls(Map<String, String> namespaceToServiceId, String host,
			int port, KubernetesMockServer kubernetesMockServer) {

		List<Endpoints> endpoints = new ArrayList<>();

		for (Map.Entry<String, String> entry : namespaceToServiceId.entrySet()) {
			Endpoints innerEndpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withName(entry.getValue()).withNamespace(entry.getKey()).build())
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(port).build())
					.withAddresses(new EndpointAddressBuilder().withIp(host).build())
					.build())
				.build();

			endpoints.add(innerEndpoints);
		}

		EndpointsList endpointsList = new EndpointsListBuilder().withItems(endpoints)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	private static List<ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			ServicePortBuilder servicePortBuilder = new ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

	private static List<EndpointPort> getEndpointPorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			EndpointPortBuilder endpointPortBuilder = new EndpointPortBuilder();
			endpointPortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				endpointPortBuilder.withName(e.getValue());
			}
			return endpointPortBuilder.build();
		}).collect(toList());
	}

}
