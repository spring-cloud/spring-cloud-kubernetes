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
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;

/**
 * @author wind57
 */
public final class Util {

	private Util() {

	}

	public static V1Service service(String namespace, String name, int port) {
		return new V1ServiceBuilder().withNewMetadata()
			.withNamespace(namespace)
			.withName(name)
			.endMetadata()
			.withSpec(new V1ServiceSpecBuilder()
				.withPorts(new V1ServicePortBuilder().withName("http").withPort(port).build())
				.build())
			.build();
	}

	public static V1Endpoints endpoints(String namespace, String name, int port, String host) {
		return new V1EndpointsBuilder()
			.withSubsets(new V1EndpointSubsetBuilder().withPorts(new CoreV1EndpointPortBuilder().withPort(port).build())
				.withAddresses(new V1EndpointAddressBuilder().withIp(host).build())
				.build())
			.withMetadata(new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
			.build();
	}

	public static void servicesPodMode(WireMockServer server, V1ServiceList serviceList) {
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/services*"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));
	}

	public static void servicesServiceMode(WireMockServer server, V1ServiceList serviceList, String serviceName) {
		// resourceVersion=0 is passed only from the watcher, so this mocks the
		// 'postConstruct' in the KubernetesInformerDiscoveryClient
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/services*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));

		server
			.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3D" + serviceName))
				.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));
	}

	public static void endpointsPodMode(WireMockServer server, V1EndpointsList endpointsList) {
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/endpoints*"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));
	}

	public static void endpointsServiceMode(WireMockServer server, V1EndpointsList endpointsList,
			String endpointsName) {
		// resourceVersion=0 is passed only from the watcher, so this mocks the
		// 'postConstruct' in the KubernetesInformerDiscoveryClient
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/endpoints*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));

		server.stubFor(
				WireMock.get(WireMock.urlEqualTo("/api/v1/endpoints?fieldSelector=metadata.name%3D" + endpointsName))
					.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));
	}

	public static void servicesInNamespacePodMode(WireMockServer server, V1ServiceList serviceList, String namespace) {
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/services*"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));
	}

	public static void servicesInNamespaceServiceMode(WireMockServer server, V1ServiceList serviceList,
			String namespace, String serviceName) {
		// resourceVersion=0 is passed only from the watcher, so this mocks the
		// 'postConstruct' in the KubernetesInformerDiscoveryClient
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/services*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));

		server.stubFor(WireMock
			.get(WireMock.urlEqualTo(
					"/api/v1/namespaces/" + namespace + "/services?fieldSelector=metadata.name%3D" + serviceName))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(serviceList)).withStatus(200)));
	}

	public static void endpointsInNamespacePodMode(WireMockServer server, V1EndpointsList endpointsList,
			String namespace) {
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/endpoints*"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));
	}

	public static void endpointsInNamespaceServiceMode(WireMockServer server, V1EndpointsList endpointsList,
			String namespace, String endpointsName) {
		// resourceVersion=0 is passed only from the watcher, so this mocks the
		// 'postConstruct' in the KubernetesInformerDiscoveryClient
		server.stubFor(WireMock.get(WireMock.urlPathMatching("^/api/v1/namespaces/" + namespace + "/endpoints*"))
			.withQueryParam("resourceVersion", WireMock.equalTo("0"))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));

		server.stubFor(WireMock
			.get(WireMock.urlEqualTo(
					"/api/v1/namespaces/" + namespace + "/endpoints?fieldSelector=metadata.name%3D" + endpointsName))
			.willReturn(WireMock.aResponse().withBody(new JSON().serialize(endpointsList)).withStatus(200)));
	}

	public static void mockWatchers(WireMockServer wireMockServer) {
		V1Service serviceA = Util.service("a", "service-a", 8888);

		V1ServiceList serviceListA = new V1ServiceListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceA)
			.build();

		servicesInNamespaceServiceMode(wireMockServer, serviceListA, "a", "service-a");

		V1Endpoints endpointsA = Util.endpoints("a", "service-a", 8888, "127.0.0.1");

		V1EndpointsList endpointsListA = new V1EndpointsListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsA)
			.build();

		Util.endpointsInNamespaceServiceMode(wireMockServer, endpointsListA, "a", "service-a");
	}

}
