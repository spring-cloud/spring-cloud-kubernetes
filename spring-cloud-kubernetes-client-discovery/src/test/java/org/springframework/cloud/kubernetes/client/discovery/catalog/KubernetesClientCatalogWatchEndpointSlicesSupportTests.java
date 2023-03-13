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

import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1APIResourceBuilder;
import io.kubernetes.client.openapi.models.V1APIResourceList;
import io.kubernetes.client.openapi.models.V1APIResourceListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.ENDPOINT_SLICE;

/**
 * Tests that only assert the needed support for EndpointSlices in the cluster.
 *
 * @author wind57
 */
class KubernetesClientCatalogWatchEndpointSlicesSupportTests {

	public static WireMockServer wireMockServer;

	private static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito
			.mock(KubernetesNamespaceProvider.class);

	private static ApiClient apiClient;

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
	}

	/**
	 * <pre>
	 *     - endpoint slices are enabled, but are not supported by the cluster, as such we will fail
	 *       with an IllegalArgumentException
	 *     - V1APIResource is empty
	 * </pre>
	 */
	@Test
	void testEndpointSlicesEnabledButNotSupported() {
		boolean useEndpointSlices = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, useEndpointSlices);

		V1APIResourceList list = new V1APIResourceListBuilder().addToResources(new V1APIResource()).build();
		stubFor(get("/apis/discovery.k8s.io/v1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(list))));

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(null, apiClient, properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	/**
	 * <pre>
	 *     - endpoint slices are enabled, but are not supported by the cluster, as such we will fail
	 *       with an IllegalArgumentException
	 *     - V1APIResource does not contain EndpointSlice
	 * </pre>
	 */
	@Test
	void testEndpointSlicesEnabledButNotSupportedViaApiVersions() {
		boolean useEndpointSlices = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, useEndpointSlices);

		V1APIResourceList list = new V1APIResourceListBuilder()
				.addToResources(new V1APIResourceBuilder().withName("not-the-one").build()).build();
		stubFor(get("/apis/discovery.k8s.io/v1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(list))));

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(null, apiClient, properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	/**
	 * endpoint slices are disabled via properties, as such we will use a catalog watch
	 * based on Endpoints
	 */
	@Test
	void testEndpointsSupport() {
		boolean useEndpointSlices = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, useEndpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(null, apiClient, properties, NAMESPACE_PROVIDER);

		Assertions.assertEquals(KubernetesEndpointsCatalogWatch.class, watch.stateGenerator().getClass());
	}

	/**
	 * endpoint slices are enabled via properties and supported by the cluster, as such we
	 * will use a catalog watch based on Endpoint Slices
	 */
	@Test
	void testEndpointSlicesSupport() {
		boolean useEndpointSlices = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, useEndpointSlices);

		V1APIResourceList list = new V1APIResourceListBuilder()
				.addToResources(new V1APIResourceBuilder().withName("endpointslices").withKind(ENDPOINT_SLICE).build())
				.build();
		stubFor(get("/apis/discovery.k8s.io/v1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(list))));

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(null, apiClient, properties, NAMESPACE_PROVIDER);
		Assertions.assertEquals(KubernetesEndpointSlicesCatalogWatch.class, watch.stateGenerator().getClass());
	}

}
