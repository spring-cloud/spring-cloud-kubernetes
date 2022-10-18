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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
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
class KubernetesClientEventBasedConfigMapChangeDetectorTests {

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

		Map<String, String> data = new HashMap<>();
		data.put("application.properties", "spring.cloud.kubernetes.configuration.watcher.refreshDelay=0\n"
				+ "logging.level.org.springframework.cloud.kubernetes=TRACE");
		Map<String, String> updateData = new HashMap<>();
		updateData.put("application.properties", "spring.cloud.kubernetes.configuration.watcher.refreshDelay=1\n"
				+ "logging.level.org.springframework.cloud.kubernetes=TRACE");
		V1ConfigMap applicationConfig = new V1ConfigMap().kind("ConfigMap")
				.metadata(new V1ObjectMeta().namespace("default").name("bar1")).data(data);
		V1ConfigMapList configMapList = new V1ConfigMapList().metadata(new V1ListMeta().resourceVersion("0"))
				.items(List.of(applicationConfig));
		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).inScenario("watch")
				.whenScenarioStateIs(STARTED).withQueryParam("watch", equalTo("false"))
				.willReturn(aResponse().withStatus(200).withBody(gson.toJson(configMapList))).willSetStateTo("update"));

		Watch.Response<V1ConfigMap> watchResponse = new Watch.Response<>(EventType.MODIFIED.name(), new V1ConfigMap()
				.kind("ConfigMap").metadata(new V1ObjectMeta().namespace("default").name("bar1")).data(updateData));
		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).inScenario("watch")
				.whenScenarioStateIs("update").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(watchResponse)))
				.willSetStateTo("add"));

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).inScenario("watch")
				.whenScenarioStateIs("add").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.ADDED.name(),
								new V1ConfigMap().kind("ConfigMap")
										.metadata(new V1ObjectMeta().namespace("default").name("bar3"))
										.putDataItem("application.properties", "debug=true")))))
				.willSetStateTo("delete"));

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).inScenario("watch")
				.whenScenarioStateIs("delete").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200)
						.withBody(new JSON().serialize(new Watch.Response<>(EventType.DELETED.name(),
								new V1ConfigMap().kind("ConfigMap")
										.metadata(new V1ObjectMeta().namespace("default").name("bar1"))
										.putDataItem("application.properties", "debug=true")))))
				.willSetStateTo("done"));

		stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*")).inScenario("watch")
				.whenScenarioStateIs("done").withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200)));
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
				mock(KubernetesClientConfigMapPropertySource.class)).withProperty("debug", "true");
		KubernetesClientConfigMapPropertySourceLocator locator = mock(
				KubernetesClientConfigMapPropertySourceLocator.class);
		when(locator.locate(environment)).thenAnswer(x -> new MockPropertySource().withProperty("debug", "false"));
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		KubernetesClientEventBasedConfigMapChangeDetector changeDetector = new KubernetesClientEventBasedConfigMapChangeDetector(
				coreV1Api, environment, ConfigReloadProperties.DEFAULT, strategy, locator, kubernetesNamespaceProvider);

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
