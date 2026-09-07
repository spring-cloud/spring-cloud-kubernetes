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

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
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

/**
 * @author Ryan Baxter
 */
class KubernetesClientEventBasedSecretsChangeDetectorTests {

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

	}

	@AfterAll
	static void afterAll() {
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

		V1Secret dbPassword = new V1Secret().metadata(new V1ObjectMeta().name("db-password").resourceVersion("1"))
			.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd".getBytes()))
			.putDataItem("password", Base64.getEncoder().encode("p455w0rd".getBytes()))
			.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()))
			.putDataItem("username", Base64.getEncoder().encode("user".getBytes()));

		V1SecretList secretList = new V1SecretList().metadata(new V1ListMeta().resourceVersion("1"))
			.items(List.of(dbPassword));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).withQueryParam("watch", equalTo("false"))
			.withQueryParam("resourceVersion", equalTo("0"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(secretList))));

		// ------------------------------------------------------------------------------------------------------------
		// 1. first watch response to request with resourceVersion=1

		V1Secret dbPasswordUpdated = new V1Secret()
			.metadata(new V1ObjectMeta().name("db-password").resourceVersion("2"))
			.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd2".getBytes()))
			.putDataItem("password", Base64.getEncoder().encode("p455w0rd2".getBytes()))
			.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()))
			.putDataItem("username", Base64.getEncoder().encode("user".getBytes()));

		Response<V1Secret> watchResponse = new Response<>(MODIFIED.name(), dbPasswordUpdated);

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("1"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponse))));

		// ------------------------------------------------------------------------------------------------------------
		// 2. second watch response to request with resourceVersion=2

		V1Secret rabbitPasswordAdded = new V1Secret().metadata(new V1ObjectMeta().name("rabbit-password"))
			.putDataItem("rabbit-pw", Base64.getEncoder().encode("password".getBytes()));

		Response<V1Secret> rabbitPasswordAddedResponse = new Response<>(ADDED.name(), rabbitPasswordAdded);

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("2"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(rabbitPasswordAddedResponse))));

		// ------------------------------------------------------------------------------------------------------------
		// 3. third watch response to request with resourceVersion=3

		V1Secret rabbitPasswordDeleted = new V1Secret().metadata(new V1ObjectMeta().name("rabbit-password"))
			.putDataItem("rabbit-pw", Base64.getEncoder().encode("password".getBytes()));

		Response<V1Secret> rabbitPasswordDeletedResponse = new Response<>(DELETED.name(), rabbitPasswordDeleted);

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("3"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(rabbitPasswordDeletedResponse))));

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
				mock(KubernetesClientSecretsPropertySource.class));

		// locator
		KubernetesClientSecretsPropertySourceLocator locator = mock(KubernetesClientSecretsPropertySourceLocator.class);
		when(locator.locate(environment))
			.thenAnswer(ignoreMe -> new MockPropertySource().withProperty("db-password", "p455w0rd2"));

		// properties
		ConfigReloadProperties properties = new ConfigReloadProperties(false, false, true,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
				Duration.ofMillis(15000), Set.of(), false, Duration.ofSeconds(2));

		// namespace provider
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		// change detector
		KubernetesClientEventBasedSecretsChangeDetector changeDetector = new KubernetesClientEventBasedSecretsChangeDetector(
				coreV1Api, environment, properties, strategy, locator, kubernetesNamespaceProvider);

		changeDetector.inform();

		// all 4 events are caught
		Awaitilities.awaitUntil(10, 1000, () -> onEventCalls[0] >= 4);

		changeDetector.shutdown();

	}

}
