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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubsetBuilder;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.assertj.core.util.Strings;

import static java.util.stream.Collectors.toList;

public final class DiscoveryClientIndexerMocks {

	private DiscoveryClientIndexerMocks() {

	}

	/**
	 * mock service indexer calls that are made when services are requested in all
	 * namespaces.
	 */
	public static void mockAllNamespacesIndexerServiceCalls(Map<String, String> namespaceToServiceId,
			WireMockServer server) {

		List<V1Service> services = new ArrayList<>();
		for (Map.Entry<String, String> entry : namespaceToServiceId.entrySet()) {
			V1Service service = new V1ServiceBuilder()
				.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP")
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

		V1ServiceList serviceList = new V1ServiceListBuilder().withItems(services)
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/services*"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));

	}

	/**
	 * mock endpoints indexer calls that are made when services are requested in all
	 * namespaces.
	 */
	public static void mockAllNamespacesIndexerEndpointsCalls(Map<String, String> namespaceToServiceId,
			WireMockServer server) {

		List<V1Endpoints> endpoints = new ArrayList<>();

		for (Map.Entry<String, String> entry : namespaceToServiceId.entrySet()) {
			V1Endpoints innerEndpoints = new V1EndpointsBuilder()
				.withMetadata(
						new V1ObjectMetaBuilder().withName(entry.getValue()).withNamespace(entry.getKey()).build())
				.withSubsets(new V1EndpointSubsetBuilder()
					.withPorts(new CoreV1EndpointPortBuilder().withPort(server.port()).build())
					.withAddresses(new V1EndpointAddressBuilder().withIp("localhost").build())
					.build())
				.build();

			endpoints.add(innerEndpoints);
		}

		V1EndpointsList endpointsList = new V1EndpointsListBuilder().withKind("V1EndpointsList")
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpoints)
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/endpoints*"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(endpointsList)).withStatus(200)));

	}

	/**
	 * mock service indexer calls that are made when services are requested in a certain
	 * namespace and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerServiceCall(String namespace, String serviceId, WireMockServer server) {

		V1Service service = new V1ServiceBuilder().withSpec(
				new V1ServiceSpecBuilder().withType("ClusterIP").withPorts(getServicePorts(Map.of(8080, "a"))).build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		V1ServiceList serviceList = new V1ServiceListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(service)
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/services*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	/**
	 * mock endpoints indexer calls that are made when services are requested in a certain
	 * namespace and the type we search by is 'SERVICE'.
	 */
	public static void mockNamespacedIndexerEndpointsCall(String namespace, String serviceId, WireMockServer server,
			int port) {

		V1Endpoints endpoints = new V1EndpointsBuilder()
			.withSubsets(new V1EndpointSubsetBuilder().withPorts(new CoreV1EndpointPortBuilder().withPort(port).build())
				.withAddresses(new V1EndpointAddressBuilder().withIp("localhost").build())
				.build())
			.withMetadata(new V1ObjectMetaBuilder().withName(serviceId).withNamespace(namespace).build())
			.build();

		V1EndpointsList endpointsList = new V1EndpointsListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpoints)
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/endpoints*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(endpointsList)).withStatus(200)));
	}

	/**
	 * mock indexer calls that are made when services are requested in a certain namespace
	 * and the call happens by labels ( "metadata.labels" )
	 */
	public static void mockNamespacedIndexerServiceCallByLabels(WireMockServer server) {

		V1ServiceList serviceList = new V1ServiceListBuilder()
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/namespaces/a/services"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.withQueryParam("labelSelector", WireMock.equalTo("same-key=same-value"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerEndpointsCallByLabels(WireMockServer server) {

		V1EndpointsList endpointsList = new V1EndpointsListBuilder()
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("/api/v1/namespaces/a/endpoints"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.withQueryParam("labelSelector", WireMock.equalTo("same-key=same-value"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(endpointsList)).withStatus(200)));
	}

	/**
	 * mock indexer calls that are made when services are requested in a certain namespace
	 * and the call happens by labels ( "metadata.labels" ) and the type we search by is
	 * 'SERVICE'.
	 */
	public static void mockNamespacedIndexerServiceCallByLabels(String namespace, WireMockServer server) {

		V1ServiceList serviceList = new V1ServiceListBuilder()
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/services*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.withQueryParam("labelSelector", WireMock.equalTo("same-key=same-value"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	/**
	 * mock indexer calls that are made when endpoints are requested in a certain
	 * namespace.
	 */
	public static void mockNamespacedIndexerEndpointsCallByLabels(String namespace, WireMockServer server) {

		// subsequent calls to watch
		V1EndpointsList endpointsList = new V1EndpointsListBuilder()
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("1").build())
			.build();

		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/endpoints*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.withQueryParam("labelSelector", WireMock.equalTo("same-key=same-value"))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(endpointsList)).withStatus(200)));
	}

	private static List<V1ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			V1ServicePortBuilder servicePortBuilder = new V1ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

}
