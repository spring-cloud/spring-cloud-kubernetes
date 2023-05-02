/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.mock.env.MockPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class KubernetesClientEventBasedSecretsChangeDetectorTests {

	private static final Map<String, StringValuePattern> WATCH_FALSE = Map.of("watch", equalTo("false"));

	private static final Map<String, StringValuePattern> WATCH_TRUE = Map.of("watch", equalTo("true"));

	private static final String SCENARIO = "watch";

	private static WireMockServer wireMockServer;

	@BeforeAll
	public static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

	}

	@AfterAll
	public static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	@Test
	void watch() {

		V1Secret dbPassword = new V1Secret().metadata(new V1ObjectMeta().name("db-password"))
				.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd".getBytes()))
				.putDataItem("password", Base64.getEncoder().encode("p455w0rd".getBytes()))
				.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()))
				.putDataItem("username", Base64.getEncoder().encode("user".getBytes()));
		V1SecretList secretList = new V1SecretList().metadata(new V1ListMeta().resourceVersion("0"))
				.items(List.of(dbPassword));

		V1Secret dbPasswordUpdated = new V1Secret().metadata(new V1ObjectMeta().name("db-password"))
				.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd2".getBytes()))
				.putDataItem("password", Base64.getEncoder().encode("p455w0rd2".getBytes()))
				.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()))
				.putDataItem("username", Base64.getEncoder().encode("user".getBytes()));
		Watch.Response<V1Secret> watchResponse = new Watch.Response<>(EventType.MODIFIED.name(), dbPasswordUpdated);

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario(SCENARIO)
				.whenScenarioStateIs(STARTED).withQueryParams(WATCH_FALSE)
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList)))
				.willSetStateTo("update"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario(SCENARIO)
				.whenScenarioStateIs("update").withQueryParams(WATCH_TRUE)
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(watchResponse)))
				.willSetStateTo("add"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario(SCENARIO).whenScenarioStateIs("add")
				.withQueryParams(WATCH_TRUE)
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.ADDED.name(),
								new V1Secret().metadata(new V1ObjectMeta().name("rabbit-password"))
										.putDataItem("rabbit-pw", Base64.getEncoder().encode("password".getBytes()))))))
				.willSetStateTo("delete"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario(SCENARIO)
				.whenScenarioStateIs("delete").withQueryParams(WATCH_TRUE)
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.DELETED.name(),
								new V1Secret().metadata(new V1ObjectMeta().name("rabbit-password"))
										.putDataItem("rabbit-pw", Base64.getEncoder().encode("password".getBytes()))))))
				.willSetStateTo("done"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario("watch").whenScenarioStateIs("done")
				.withQueryParam("watch", equalTo("true")).willReturn(aResponse().withStatus(200)));

		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
		apiClient.setHttpClient(httpClient);
		CoreV1Api coreV1Api = new CoreV1Api(apiClient);

		int[] howMany = new int[1];
		Runnable run = () -> {
			++howMany[0];
		};
		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("strategy", run);

		KubernetesMockEnvironment environment = new KubernetesMockEnvironment(
				mock(KubernetesClientSecretsPropertySource.class)).withProperty("db-password", "p455w0rd");
		KubernetesClientSecretsPropertySourceLocator locator = mock(KubernetesClientSecretsPropertySourceLocator.class);
		when(locator.locate(environment))
				.thenAnswer(ignoreMe -> new MockPropertySource().withProperty("db-password", "p455w0rd2"));
		ConfigReloadProperties properties = new ConfigReloadProperties(false, false, true,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
				Duration.ofMillis(15000), Set.of(), false, Duration.ofSeconds(2));
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");
		KubernetesClientEventBasedSecretsChangeDetector changeDetector = new KubernetesClientEventBasedSecretsChangeDetector(
				coreV1Api, environment, properties, strategy, locator, kubernetesNamespaceProvider);

		Thread controllerThread = new Thread(changeDetector::inform);
		controllerThread.setDaemon(true);
		controllerThread.start();

		await().timeout(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(2)).until(() -> howMany[0] >= 4);
	}

	/**
	 * both are null, treat that as no change.
	 */
	@Test
	void equalsOne() {
		Map<String, byte[]> left = null;
		Map<String, byte[]> right = null;

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertTrue(result);
	}

	/**
	 * - left is empty map - right is null
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void equalsTwo() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = null;

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertTrue(result);
	}

	/**
	 * - left is empty map - right is null
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void equalsThree() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = null;

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertTrue(result);
	}

	/**
	 * - left is null - right is empty map
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void equalsFour() {
		Map<String, byte[]> left = null;
		Map<String, byte[]> right = Map.of();

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertTrue(result);
	}

	/**
	 * - left is empty map - right is empty map
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void equalsFive() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = Map.of();

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertTrue(result);
	}

	/**
	 * - left is empty map - right is [1, b]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void equalsSix() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = Map.of("1", "b".getBytes());

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertFalse(result);
	}

	/**
	 * - left is [1, a] - right is [1, b]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void equalsSeven() {
		Map<String, byte[]> left = Map.of("1", "a".getBytes());
		Map<String, byte[]> right = Map.of("1", "b".getBytes());

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertFalse(result);
	}

	/**
	 * - left is [1, a, 2 aa] - right is [1, b, 2, aa]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void equalsEight() {
		Map<String, byte[]> left = Map.of("1", "a".getBytes(), "2", "aa".getBytes());
		Map<String, byte[]> right = Map.of("1", "b".getBytes(), "2", "aa".getBytes());

		boolean result = KubernetesClientEventBasedSecretsChangeDetector.equals(left, right);
		Assertions.assertFalse(result);
	}

}
