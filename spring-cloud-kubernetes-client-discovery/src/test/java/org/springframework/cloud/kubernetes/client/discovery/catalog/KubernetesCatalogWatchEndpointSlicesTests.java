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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Test cases for the Endpoint Slices support
 *
 * @author wind57
 */
class KubernetesCatalogWatchEndpointSlicesTests extends KubernetesEndpointsAndEndpointSlicesTests {

	private static final Boolean USE_ENDPOINT_SLICES = true;

	private static ApiClient apiClient;

	public static WireMockServer wireMockServer;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
	}

	@AfterAll
	public static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
		Mockito.reset(APPLICATION_EVENT_PUBLISHER);
	}

	@Test
	@Override
	void testInAllNamespacesEmptyServiceLabels() {
		stubFor(get("/apis/discovery.k8s.io/v1/endpointslices?labelSelector=").willReturn(
				aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "default")))));
		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(Map.of(), Set.of(), null, apiClient,
				USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "default")));
	}

	@Test
	@Override
	void testInAllNamespacesWithSingleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/endpointslices?labelSelector=a%3Db").willReturn(
				aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "default")))));
		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(Map.of("a", "b"), Set.of(), null,
				apiClient, USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "default")));
	}

	@Test
	@Override
	void testInAllNamespacesWithDoubleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/endpointslices?labelSelector=a%3Db%26c%3Dd").willReturn(
				aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "default")))));
		// otherwise the stub might fail
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("a", "b");
		map.put("c", "d");
		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(map, Set.of(), null, apiClient,
				USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "default")));
	}

	@Test
	@Override
	void testInSpecificNamespacesEmptyServiceLabels() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/b/endpointslices?labelSelector=")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "b")))));
		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesWithLabels(Set.of("b"), Map.of(), null,
				apiClient, USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "b")));
	}

	@Test
	@Override
	void testInSpecificNamespacesWithSingleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/one/endpointslices?labelSelector=a%3Db")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("aa", "a")))));
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/two/endpointslices?labelSelector=a%3Db")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("bb", "b")))));

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesWithLabels(Set.of("one", "two"),
				Map.of("a", "b"), null, apiClient, USE_ENDPOINT_SLICES);

		invokeAndAssert(watch,
				List.of(new EndpointNameAndNamespace("aa", "a"), new EndpointNameAndNamespace("bb", "b")));
	}

	@Test
	@Override
	void testInSpecificNamespacesWithDoubleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/one/endpointslices?labelSelector=a%3Db%26c%3Dd")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("aa", "a")))));
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/two/endpointslices?labelSelector=a%3Db%26c%3Dd")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("bb", "b")))));

		// otherwise the stub might fail
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("a", "b");
		map.put("c", "d");

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesWithLabels(Set.of("one", "two"), map, null,
				apiClient, USE_ENDPOINT_SLICES);

		invokeAndAssert(watch,
				List.of(new EndpointNameAndNamespace("aa", "a"), new EndpointNameAndNamespace("bb", "b")));
	}

	@Test
	@Override
	void testInOneNamespaceEmptyServiceLabels() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/b/endpointslices?labelSelector=")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "b")))));
		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("b", Map.of(), null, apiClient,
				USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "b")));
	}

	@Test
	@Override
	void testInOneNamespaceWithSingleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/b/endpointslices?labelSelector=key%3Dvalue")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "b")))));
		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("b", Map.of("key", "value"), null,
				apiClient, USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "b")));
	}

	@Test
	@Override
	void testInOneNamespaceWithDoubleLabel() {
		stubFor(get("/apis/discovery.k8s.io/v1/namespaces/b/endpointslices?labelSelector=key%3Dvalue%26key1%3Dvalue1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(endpointSlices("a", "b")))));
		// otherwise the stub might fail
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("key", "value");
		map.put("key1", "value1");
		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("b", map, null, apiClient,
				USE_ENDPOINT_SLICES);

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("a", "b")));
	}

}
