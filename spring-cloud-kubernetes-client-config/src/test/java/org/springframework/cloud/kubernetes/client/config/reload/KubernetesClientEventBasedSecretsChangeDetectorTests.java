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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
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
		GsonBuilder builder = new GsonBuilder();
		builder.excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT, Modifier.VOLATILE)
				.registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTimeAdapter());
		Gson gson = builder.create();

		V1Secret dbPassword = new V1Secret().kind("Secret").metadata(new V1ObjectMeta().name("db-password"))
				.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd".getBytes()))
				.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()));
		V1Secret dbPasswordUpdated = new V1Secret().kind("Secret").metadata(new V1ObjectMeta().name("db-password"))
				.putStringDataItem("password", Base64.getEncoder().encodeToString("p455w0rd2".getBytes()))
				.putStringDataItem("username", Base64.getEncoder().encodeToString("user".getBytes()));
		V1SecretList secretList = new V1SecretList().kind("SecretList").metadata(new V1ListMeta().resourceVersion("0"))
				.items(List.of(dbPassword));

		stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*")).inScenario("watch")
				.whenScenarioStateIs(STARTED).withQueryParam("watch", equalTo("false"))
				.willReturn(aResponse().withStatus(200).withBody(gson.toJson(secretList))).willSetStateTo("update"));

		Watch.Response<V1Secret> watchResponse = new Watch.Response<>(EventType.MODIFIED.name(), dbPasswordUpdated);
		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario("watch")
				.whenScenarioStateIs("update").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(watchResponse)))
				.willSetStateTo("add"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario("watch").whenScenarioStateIs("add")
				.withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.ADDED.name(),
								new V1Secret().kind("Secret").metadata(new V1ObjectMeta().name("rabbit-password"))
										.putDataItem("rabbit-pw", Base64.getEncoder().encode("password".getBytes()))))))
				.willSetStateTo("delete"));

		stubFor(get(urlMatching("/api/v1/namespaces/default/secrets.*")).inScenario("watch")
				.whenScenarioStateIs("delete").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.DELETED.name(),
								new V1Secret().kind("Secret").metadata(new V1ObjectMeta().name("rabbit-password"))
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

	// This is needed when using JDK17 because GSON uses reflection to construct an
	// OffsetDateTime but that constructor
	// is protected.
	public final static class GsonOffsetDateTimeAdapter extends TypeAdapter<OffsetDateTime> {

		@Override
		public void write(JsonWriter jsonWriter, OffsetDateTime localDateTime) throws IOException {
			jsonWriter.value(OffsetDateTime.now().toString());
		}

		@Override
		public OffsetDateTime read(JsonReader jsonReader) {
			return OffsetDateTime.now();
		}

	}

}
