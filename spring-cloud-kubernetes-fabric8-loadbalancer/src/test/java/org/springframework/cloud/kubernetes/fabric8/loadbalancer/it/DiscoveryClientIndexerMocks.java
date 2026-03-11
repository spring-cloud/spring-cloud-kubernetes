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
 * Mock methods that are part of the discovery client indexers initialization.
 *
 * @author wind57
 */
public final class DiscoveryClientIndexerMocks {

	private DiscoveryClientIndexerMocks() {

	}

	/**
	 * mock service indexer calls that are made when services are requested in a certain
	 * namespace and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerServiceCall(KubernetesMockServer kubernetesMockServer) {

		String namespace = "a";

		// first call to populate shared index informer
		ServiceList serviceList = new ServiceListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/services?resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		ServiceList emptyServiceList = new ServiceListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
				+ "/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock service indexer calls that are made when services are requested in a certain
	 * namespace and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerServiceCall(String namespace, String serviceId,
		KubernetesMockServer kubernetesMockServer) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(8080, "a")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		// first call to populate shared index informer
		ServiceList serviceList = new ServiceListBuilder().withItems(service)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/services?resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		ServiceList emptyServiceList = new ServiceListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
				+ "/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock endpoints indexer calls that are made when services are requested in a certain
	 * namespace  and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerEndpointsCall(KubernetesMockServer kubernetesMockServer) {

		String namespace = "a";

		// subsequent calls to watch
		EndpointsList endpointsList = new EndpointsListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/endpoints?resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
				+ "/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock endpoints indexer calls that are made when services are requested in a certain
	 * namespace  and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerEndpointsCall(String namespace, String serviceId,
			KubernetesMockServer kubernetesMockServer, int port) {

		Endpoints endpoints = new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withName(serviceId).withNamespace(namespace).build())
			.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(port).build())
				.withAddresses(new EndpointAddressBuilder().withIp("localhost").build())
				.build())
			.build();

		// subsequent calls to watch
		EndpointsList endpointsList = new EndpointsListBuilder()
			.withItems(endpoints)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/endpoints?resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace
				+ "/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock indexer calls that are made when services are requested in all namespaces.
	 * there is a labelSelector as a filter {a=service-a}.
	 */
	public static void mockAllNamespacesIndexerEndpointsCallsWithLabels(String namespace, String serviceId,
			int port, KubernetesMockServer kubernetesMockServer) {

		Endpoints endpoints = new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withName(serviceId).withNamespace(namespace).build())
			.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(port).build())
				.withAddresses(new EndpointAddressBuilder().withIp("localhost").build())
				.build())
			.build();

		EndpointsList endpointsList = new EndpointsListBuilder().withItems(endpoints)
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock service indexer calls that are made when services are requested in all namespaces.
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

	/**
	 * mock indexer calls that are made when services are requested in all namespaces.
	 * there is a labelSelector as a filter {a=service-a}
	 */
	public static void mockAllNamespacesIndexerServiceCallsWithLabels(KubernetesMockServer kubernetesMockServer) {

		ServiceList serviceList = new ServiceListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		ServiceList emptyServiceList = new ServiceListBuilder().build();

		// first call to populate shared index informer
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock endpoints indexer calls that are made when services are requested in all namespaces.
	 */
	public static void mockAllNamespacesIndexerEndpointsCalls(Map<String, String> namespaceToServiceId,
		KubernetesMockServer kubernetesMockServer) {

		List<Endpoints> endpoints = new ArrayList<>();

		for (Map.Entry<String, String> entry : namespaceToServiceId.entrySet()) {
			Endpoints innerEndpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withName(entry.getValue()).withNamespace(entry.getKey()).build())
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder()
						.withPort(kubernetesMockServer.getPort()).build())
					.withAddresses(new EndpointAddressBuilder().withIp("localhost").build())
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

	/**
	 * mock indexer calls that are made when services are requested in all namespaces.
	 * there is a labelSelector as a filter {a=service-a}.
	 */
	public static void mockAllNamespacesIndexerEndpointsCallsWithLabels(KubernetesMockServer kubernetesMockServer) {

		EndpointsList endpointsList = new EndpointsListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();

		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock indexer calls that are made when services are requested in a certain
	 * namespace and the call happens by labels ( "metadata.labels" )
	 */
	public static void mockNamespacedIndexerServiceCallByLabels(KubernetesMockServer kubernetesMockServer) {

		// first call to populate shared index informer
		ServiceList serviceList = new ServiceListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/a/services?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		ServiceList emptyServiceList = new ServiceListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/a/services?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock indexer calls that are made when services are requested in a certain
	 * namespace and the call happens by labels ( "metadata.labels" ) and the type
	 * we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerServiceCallByLabels(String namespace,
			KubernetesMockServer kubernetesMockServer) {

		// first call to populate shared index informer
		ServiceList serviceList = new ServiceListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/services?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, serviceList)
			.once();

		// subsequent calls to watch
		ServiceList emptyServiceList = new ServiceListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/services?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyServiceList)
			.always();
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerEndpointsCallByLabels(KubernetesMockServer kubernetesMockServer) {

		// subsequent calls to watch
		EndpointsList endpointsList = new EndpointsListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/a/endpoints?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/a/endpoints?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, emptyEndpointsList)
			.always();
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerEndpointsCallByLabels(String namespace,
			KubernetesMockServer kubernetesMockServer) {

		// subsequent calls to watch
		EndpointsList endpointsList = new EndpointsListBuilder()
			.withMetadata(new ListMetaBuilder().withResourceVersion("1").build())
			.build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/endpoints?labelSelector=same-key%3Dsame-value&resourceVersion=0")
			.andReturn(200, endpointsList)
			.once();

		// subsequent calls to watch
		EndpointsList emptyEndpointsList = new EndpointsListBuilder().build();
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/" + namespace + "/endpoints?allowWatchBookmarks=true&labelSelector=same-key%3Dsame-value&resourceVersion=1&timeoutSeconds=600&watch=true")
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

}
