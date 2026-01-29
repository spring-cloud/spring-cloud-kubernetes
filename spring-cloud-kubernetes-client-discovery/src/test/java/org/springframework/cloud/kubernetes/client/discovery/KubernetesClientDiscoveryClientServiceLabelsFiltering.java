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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.ClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
abstract class KubernetesClientDiscoveryClientServiceLabelsFiltering {

	@RegisterExtension
	static final WireMockExtension API_SERVER = WireMockExtension.newInstance()
		.options(options().dynamicPort())
		.build();

	private static final KubernetesClientInformerAutoConfiguration CONFIGURATION = new KubernetesClientInformerAutoConfiguration();

	private static ApiClient apiClient;

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void beforeAll() {
		WireMock.configureFor("localhost", API_SERVER.getPort());
		apiClient = new ClientBuilder().setBasePath("http://localhost:" + API_SERVER.getPort()).build();
		coreV1Api = new CoreV1Api(apiClient);
	}

	@AfterAll
	static void afterAll() {
		API_SERVER.shutdownServer();
	}

	@BeforeEach
	void beforeEach() {
		mockAllPossibleCalls();
	}

	@AfterEach
	void afterEach() {
		API_SERVER.resetAll();
	}

	private static final V1Service NAMESPACE_A__SERVICE_X = service("serviceX", "namespaceA",
			Map.of("color", "red", "namespace", "a"));

	private static final V1Service NAMESPACE_A__SERVICE_XX = service("serviceXX", "namespaceA",
			Map.of("color", "green", "namespace", "a"));

	private static final V1Endpoints NAMESPACE_A__ENDPOINTS_X = endpoints("serviceX", "namespaceA",
			Map.of("color", "red", "namespace", "a"));

	private static final V1Endpoints NAMESPACE_A__ENDPOINTS_XX = endpoints("serviceXX", "namespaceA",
			Map.of("color", "green", "namespace", "a"));

	private static final V1Service NAMESPACE_B__SERVICE_X = service("serviceX", "namespaceB",
			Map.of("color", "red", "namespace", "b"));

	private static final V1Service NAMESPACE_B__SERVICE_XX = service("serviceXX", "namespaceB",
			Map.of("color", "green", "namespace", "b"));

	private static final V1Endpoints NAMESPACE_B__ENDPOINTS_X = endpoints("serviceX", "namespaceB",
			Map.of("color", "red", "namespace", "b"));

	private static final V1Endpoints NAMESPACE_B__ENDPOINTS_XX = endpoints("serviceXX", "namespaceB",
			Map.of("color", "green", "namespace", "b"));

	void mockAllPossibleCalls() {
		mockServicesCallNamespaceAServiceX();
		mockServicesCallNamespaceAServiceXX();

		mockEndpointsCallNamespaceAServiceX();
		mockEndpointsCallNamespaceAServiceXX();

		mockServicesCallNamespaceBServiceX();
		mockServicesCallNamespaceBServiceXX();

		mockEndpointsCallNamespaceBServiceX();
		mockEndpointsCallNamespaceBServiceXX();

		mockServicesCallNamespaceANoLabels();
		mockEndpointsCallNamespaceANoLabels();

		mockServicesCallNamespaceBNoLabels();
		mockEndpointsCallNamespaceBNoLabels();

		mockServicesCallAllNamespacesRedLabel();
		mockEndpointsCallAllNamespacesRedLabel();

		mockServicesCallAllNamespacesGreenLabel();
		mockEndpointsCallAllNamespacesGreenLabel();

		mockServicesCallAllNamespacesNoLabels();
		mockEndpointsCallAllNamespacesNoLabels();
	}

	KubernetesClientInformerDiscoveryClient createAndStartListers(List<String> namespaces,
			KubernetesDiscoveryProperties properties) {
		List<SharedInformerFactory> sharedInformerFactories = CONFIGURATION.sharedInformerFactories(apiClient,
				namespaces);

		List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers = CONFIGURATION
			.serviceSharedIndexInformers(sharedInformerFactories, namespaces, coreV1Api, properties);
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers = CONFIGURATION
			.endpointsSharedIndexInformers(sharedInformerFactories, namespaces, coreV1Api, properties);

		List<Lister<V1Service>> serviceListers = CONFIGURATION.serviceListers(namespaces, serviceSharedIndexInformers);
		List<Lister<V1Endpoints>> endpointsListers = CONFIGURATION.endpointsListers(namespaces,
				endpointsSharedIndexInformers);

		startInformers(sharedInformerFactories, serviceSharedIndexInformers, endpointsSharedIndexInformers);

		return new KubernetesClientInformerDiscoveryClient(sharedInformerFactories, serviceListers, endpointsListers,
				null, null, properties, coreV1Api, x -> true);
	}

	/**
	 * <pre>
	 *  lots of mock and helpers follow
	 *  ***********************************************************************************************************
	 *  ***********************************************************************************************************
	 * </pre>
	 */
	private static void mockServicesCallNamespaceAServiceX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__SERVICE_X)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallNamespaceAServiceXX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__SERVICE_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceAServiceX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__ENDPOINTS_X)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceAServiceXX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__ENDPOINTS_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallNamespaceBServiceX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__SERVICE_X)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallNamespaceBServiceXX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__SERVICE_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceBServiceX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__ENDPOINTS_X)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceBServiceXX() {

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__ENDPOINTS_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallNamespaceANoLabels() {
		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__SERVICE_X)
							.addItemsItem(NAMESPACE_A__SERVICE_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/namespaces/namespaceA/services"))
			.withQueryParam("labelSelector", WireMock.absent())
			.withQueryParam("watch", equalTo("true"))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceANoLabels() {
		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_A__ENDPOINTS_X)
							.addItemsItem(NAMESPACE_A__ENDPOINTS_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/namespaces/namespaceA/endpoints"))
			.withQueryParam("labelSelector", WireMock.absent())
			.withQueryParam("watch", equalTo("true"))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallNamespaceBNoLabels() {
		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__SERVICE_X)
							.addItemsItem(NAMESPACE_B__SERVICE_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/services")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallNamespaceBNoLabels() {
		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("false"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200)
						.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
							.addItemsItem(NAMESPACE_B__ENDPOINTS_X)
							.addItemsItem(NAMESPACE_B__ENDPOINTS_XX)))));

		API_SERVER.stubFor(
				get(urlPathEqualTo("/api/v1/namespaces/namespaceB/endpoints")).withQueryParam("watch", equalTo("true"))
					.withQueryParam("labelSelector", WireMock.absent())
					.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallAllNamespacesRedLabel() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__SERVICE_X)
					.addItemsItem(NAMESPACE_B__SERVICE_X)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallAllNamespacesRedLabel() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__ENDPOINTS_X)
					.addItemsItem(NAMESPACE_B__ENDPOINTS_X)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "red"))))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallAllNamespacesGreenLabel() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__SERVICE_XX)
					.addItemsItem(NAMESPACE_B__SERVICE_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallAllNamespacesGreenLabel() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__ENDPOINTS_XX)
					.addItemsItem(NAMESPACE_B__ENDPOINTS_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector(Map.of("color", "green"))))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCallAllNamespacesNoLabels() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", absent())
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__SERVICE_X)
					.addItemsItem(NAMESPACE_A__SERVICE_XX)
					.addItemsItem(NAMESPACE_B__SERVICE_X)
					.addItemsItem(NAMESPACE_B__SERVICE_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/services")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", absent())
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockEndpointsCallAllNamespacesNoLabels() {
		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", absent())
			.willReturn(aResponse().withStatus(200)
				.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(NAMESPACE_A__ENDPOINTS_X)
					.addItemsItem(NAMESPACE_A__ENDPOINTS_XX)
					.addItemsItem(NAMESPACE_B__ENDPOINTS_X)
					.addItemsItem(NAMESPACE_B__ENDPOINTS_XX)))));

		API_SERVER.stubFor(get(urlPathEqualTo("/api/v1/endpoints")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", absent())
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static V1Service service(String name, String namespace, Map<String, String> labels) {
		return new V1Service().metadata(new V1ObjectMeta().name(name).namespace(namespace).labels(labels))
			.spec(new V1ServiceSpec().type("ClusterIP"));
	}

	private static V1Endpoints endpoints(String name, String namespace, Map<String, String> labels) {
		return new V1Endpoints().metadata(new V1ObjectMeta().name(name).namespace(namespace).labels(labels))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet()
			.stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining(","));
	}

	private void startInformers(List<SharedInformerFactory> sharedInformerFactories,
			List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers,
			List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers) {
		sharedInformerFactories.forEach(SharedInformerFactory::startAllRegisteredInformers);

		Awaitility.await()
			.until(() -> serviceSharedIndexInformers.stream()
				.map(SharedIndexInformer::hasSynced)
				.reduce(Boolean::logicalAnd)
				.orElse(false));

		Awaitility.await()
			.until(() -> endpointsSharedIndexInformers.stream()
				.map(SharedIndexInformer::hasSynced)
				.reduce(Boolean::logicalAnd)
				.orElse(false));
	}

}
