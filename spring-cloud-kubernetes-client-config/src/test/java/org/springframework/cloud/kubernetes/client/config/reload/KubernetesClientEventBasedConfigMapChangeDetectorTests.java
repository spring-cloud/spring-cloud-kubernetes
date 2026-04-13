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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.mock.env.MockPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.kubernetes.client.informer.EventType.ADDED;
import static io.kubernetes.client.informer.EventType.DELETED;
import static io.kubernetes.client.informer.EventType.MODIFIED;
import static io.kubernetes.client.util.Watch.Response;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.commons.config.Constants.APPLICATION_PROPERTIES;

/**
 * @author Ryan Baxter
 */
class KubernetesClientEventBasedConfigMapChangeDetectorTests {

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());
	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@Test
	void watch() {

		// ------------------------------------------------------------------------------------------------------------
		// 0. initial request of the informer ( resourceVersion=0 )
		Map<String, String> myConfigInitial = Map.of(APPLICATION_PROPERTIES,
				"spring.cloud.kubernetes.configuration.watcher.refreshDelay=0");

		V1ConfigMap myConfigMapInitial = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default").name("my-configmap"))
			.data(myConfigInitial);
		V1ConfigMapList myConfigMapListInitial = new V1ConfigMapList().metadata(new V1ListMeta().resourceVersion("1"))
			.items(List.of(myConfigMapInitial));

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("resourceVersion", equalTo("0"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(myConfigMapListInitial))));

		// ------------------------------------------------------------------------------------------------------------
		// 1. first watch response to request with resourceVersion=1
		Map<String, String> myConfigChanged = Map.of(APPLICATION_PROPERTIES,
				"spring.cloud.kubernetes.configuration.watcher.refreshDelay=1");

		V1ConfigMap myConfigMapChanged = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default").name("my-configmap").resourceVersion("2"))
			.data(myConfigChanged);

		Response<V1ConfigMap> watchResponseOne = new Response<>(MODIFIED.name(), myConfigMapChanged);

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("1"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponseOne))));

		// ------------------------------------------------------------------------------------------------------------
		// 2. second watch response to request with resourceVersion=2

		Map<String, String> newConfigAdded = Map.of(APPLICATION_PROPERTIES, "debug=true");

		V1ConfigMap newConfigMapAdded = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default").name("new-configmap").resourceVersion("3"))
			.data(newConfigAdded);

		Response<V1ConfigMap> watchResponseTwo = new Response<>(ADDED.name(), newConfigMapAdded);

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("2"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponseTwo))));

		// ------------------------------------------------------------------------------------------------------------
		// 3. third watch response to request with resourceVersion=3

		Map<String, String> newConfigDeleted = Map.of(APPLICATION_PROPERTIES, "debug=true");

		V1ConfigMap newConfigMapDeleted = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default").name("new-configmap").resourceVersion("4"))
			.data(newConfigDeleted);

		Response<V1ConfigMap> watchResponseThree = new Response<>(DELETED.name(), newConfigMapDeleted);

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("3"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponseThree))));

		// ------------------------------------------------------------------------------------------------------------
		// 4. assertions

		changeDetectorAssert();
	}

	private void changeDetectorAssert() {

		// coreV1Api
		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		CoreV1Api coreV1Api = new CoreV1Api(apiClient);

		// update strategy
		int[] onEventCalls = new int[1];
		Runnable run = () -> ++onEventCalls[0];
		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("strategy", run);

		// mock environment
		KubernetesMockEnvironment environment = new KubernetesMockEnvironment(
				mock(KubernetesClientConfigMapPropertySource.class));

		// locator
		KubernetesClientConfigMapPropertySourceLocator locator = mock(
				KubernetesClientConfigMapPropertySourceLocator.class);
		when(locator.locate(environment)).thenAnswer(x -> new MockPropertySource().withProperty("debug", "false"));

		// namespace provider
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		// change detector
		KubernetesClientEventBasedConfigMapChangeDetector changeDetector = new KubernetesClientEventBasedConfigMapChangeDetector(
				coreV1Api, environment, ConfigReloadProperties.DEFAULT, strategy, locator, kubernetesNamespaceProvider);

		changeDetector.inform();

		// all 4 events are caught
		Awaitilities.awaitUntil(10, 1000, () -> onEventCalls[0] == 4);

		changeDetector.shutdown();

	}

}
