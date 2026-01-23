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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryClientServiceLabelsFilteringTests {

	private static final String NAMESPACE_A = "namespaceA";

	private static final String NAMESPACE_B = "namespaceB";

	private static final String SERVICE_NAME = "serviceX";

	private static final String ENDPOINTS_NAME = "endpointsX";

	private static final Map<String, String> SHAPE_CIRCLE = Map.of("shape", "circle");

	private static final Map<String, String> SHAPE_TRIANGLE = Map.of("shape", "triangle");

	private static final KubernetesClientInformerAutoConfiguration CONFIGURATION = new KubernetesClientInformerAutoConfiguration();

	private static ApiClient apiClient;

	private static CoreV1Api coreV1Api;

	@RegisterExtension
	private static final WireMockExtension API_SERVER = WireMockExtension.newInstance()
		.options(options().dynamicPort())
		.build();

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

	@AfterEach
	void afterEach() {
		API_SERVER.resetAll();
	}

	/**
	 * //TODO
	 */
	@Test
	void testServicesWithDifferentMetadataLabels() {

		boolean discoveryInAllNamespaces = false;
		Set<String> selectiveNamespaces = Set.of(NAMESPACE_A, NAMESPACE_B);
		List<String> selectiveNamespacesAsList = List.of(NAMESPACE_A, NAMESPACE_B);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, discoveryInAllNamespaces,
			selectiveNamespaces, true, 60L, false, null, Set.of(), SHAPE_CIRCLE, null,
			KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		service(NAMESPACE_A, SHAPE_CIRCLE);
		service(NAMESPACE_B, SHAPE_CIRCLE);
		service(NAMESPACE_B, SHAPE_TRIANGLE);

		endpoints(NAMESPACE_A, SHAPE_CIRCLE);
		endpoints(NAMESPACE_B, SHAPE_CIRCLE);
		endpoints(NAMESPACE_B, SHAPE_TRIANGLE);

		List<SharedInformerFactory> sharedInformerFactories =
			CONFIGURATION.sharedInformerFactories(apiClient, selectiveNamespacesAsList);

		List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers = CONFIGURATION.serviceSharedIndexInformers(
			sharedInformerFactories, selectiveNamespacesAsList, coreV1Api , properties);
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers = CONFIGURATION.endpointsSharedIndexInformers(
			sharedInformerFactories, selectiveNamespacesAsList, coreV1Api , properties);

		List<Lister<V1Service>> serviceListers = CONFIGURATION.serviceListers(selectiveNamespacesAsList,
			serviceSharedIndexInformers);
		List<Lister<V1Endpoints>> endpointsListers = CONFIGURATION.endpointsListers(selectiveNamespacesAsList,
			endpointsSharedIndexInformers);

		startInformers(sharedInformerFactories, serviceSharedIndexInformers, endpointsSharedIndexInformers);

		KubernetesClientInformerDiscoveryClient discoveryClient = new KubernetesClientInformerDiscoveryClient(
			sharedInformerFactories, serviceListers, endpointsListers, null, null,
			properties, coreV1Api, x -> true);

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances(SERVICE_NAME);
		assertThat(serviceInstances.size()).isEqualTo(1);
		assertThat(serviceInstances.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	private void service(String namespace, Map<String, String> labels) {
		V1Service service = new V1Service().metadata(new V1ObjectMeta().name(SERVICE_NAME).namespace(namespace).labels(labels))
			.spec(new V1ServiceSpec().type("ClusterIP"));
		mockServicesCall(namespace, labelSelector(labels), service);
	}

	private void endpoints(String namespace, Map<String, String> labels) {
		V1Endpoints endpoints = new V1Endpoints().metadata(new V1ObjectMeta().name(ENDPOINTS_NAME).namespace(namespace).labels(labels))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
		mockEndpointsCall(namespace, labelSelector(labels), endpoints);
	}

	private static void mockEndpointsCall(String namespace, String labelSelector,
		V1Endpoints endpoints) {

		// watch=false, first call to populate watcher cache
		// this is when we provide the Endpoints
		KubernetesClientDiscoveryClientServiceLabelsFilteringTests.API_SERVER
			.stubFor(get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/endpoints"))
			.withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector))
			.willReturn(aResponse()
				.withStatus(200)
				.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(endpoints)))));

		// watch=true, call to re-sync
		// nothing new is incoming when we re-sync
		KubernetesClientDiscoveryClientServiceLabelsFilteringTests.API_SERVER
			.stubFor(get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/endpoints"))
			.withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCall(String namespace, String labelSelector,
		V1Service service) {

		// watch=false, first call to populate watcher cache
		// this is when we provide the Service
		KubernetesClientDiscoveryClientServiceLabelsFilteringTests.API_SERVER
			.stubFor(get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/services"))
			.withQueryParam("watch", equalTo("false"))
			.withQueryParam("labelSelector", equalTo(labelSelector))
			.willReturn(aResponse()
				.withStatus(200)
				.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(service)))));

		// watch=true, call to re-sync
		// nothing new is incoming when we re-sync
		KubernetesClientDiscoveryClientServiceLabelsFilteringTests.API_SERVER
			.stubFor(get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/services"))
			.withQueryParam("watch", equalTo("true"))
			.withQueryParam("labelSelector", equalTo(labelSelector))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining("&"));
	}

	private void startInformers(List<SharedInformerFactory> sharedInformerFactories,
			List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers,
			List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers) {
		sharedInformerFactories.forEach(SharedInformerFactory::startAllRegisteredInformers);

		Awaitility.await().until(() -> serviceSharedIndexInformers.stream()
			.map(SharedIndexInformer::hasSynced)
			.reduce(Boolean::logicalAnd)
			.orElse(false));

		Awaitility.await().until(() -> endpointsSharedIndexInformers.stream()
			.map(SharedIndexInformer::hasSynced)
			.reduce(Boolean::logicalAnd)
			.orElse(false));
	}

}
