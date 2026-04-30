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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
import io.kubernetes.client.util.Watch;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.kubernetes.client.informer.EventType.MODIFIED;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		classes = SecretWatcherWithLabelsTest.TestConfig.class,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.config.import=",
				"spring.cloud.kubernetes.reload.enabled=false", "spring.cloud.kubernetes.discovery.enabled=false",
				"spring.cloud.kubernetes.reload.mode=EVENT",
				"spring.cloud.kubernetes.reload.monitoring-config-maps=false",
				"spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.secrets.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true",
				"spring.cloud.kubernetes.reload.secrets-labels[spring.cloud.kubernetes.secret]=true",
				"spring.cloud.kubernetes.configuration.watcher.refresh-delay=1ms" })
class SecretWatcherWithLabelsTest {

	private static final String PATH = "^/api/v1/namespaces/default/secrets.*";

	private static final MockedStatic<KubernetesClientUtils> KUBERNETES_CLIENT_UTILS_MOCKED_STATIC = Mockito
		.mockStatic(KubernetesClientUtils.class);

	private static WireMockServer wireMockServer;

	private static final List<String> OBSERVED_COLORS = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.eq(null),
				Mockito.anyString(), Mockito.any(KubernetesNamespaceProvider.class)))
			.thenReturn("default");

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.when(KubernetesClientUtils::kubernetesApiClient).thenReturn(apiClient);

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient)
			.thenReturn(apiClient);

		stubWatcher();

	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.close();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	/**
	 * <pre>
	 *   	There are two shared informers that are created in the configuration watcher
	 *     		- HttpBasedSecretsWatchChangeDetector that has onEvent that delegates to
	 *       	  WatcherUtil::onEvent
	 *     		- KubernetesClientEventBasedSecretsChangeDetector
	 *
	 *      The first one is needed to be able to restart apps via the actuator, for example.
	 *      The second one is needed to reload properties of the configuration watcher itself.
	 *      In this test, we only care about the KubernetesClientEventBasedSecretsChangeDetector, as such we will set:
	 *
	 *          spring.cloud.kubernetes.reload.enabled=false
	 *
	 *      We set-up the informer to catch two calls : one where the secret has color=white ( the first one )
	 *      and then color=blue, in the watch modified event.
	 * </pre>
	 */
	@Test
	void test() {
		Awaitilities.awaitUntil(10, 1000, () -> OBSERVED_COLORS.size() == 2);
		Assertions.assertThat(OBSERVED_COLORS).containsExactly("white", "blue");
	}

	private static void stubWatcher() {
		// ------------------------------------------------------------------------------------------------------------
		// 0. initial request of the informer ( resourceVersion=0 )
		// initial color is white

		V1Secret mySecretInitial = new V1Secret()
			.metadata(new V1ObjectMeta().namespace("default")
				.labels(Map.of("spring.cloud.kubernetes.secret", "true"))
				.name("my-secret"))
			.data(Map.of("color", "white".getBytes(StandardCharsets.UTF_8)));
		V1SecretList mySecretListInitial = new V1SecretList().metadata(new V1ListMeta().resourceVersion("1"))
			.items(List.of(mySecretInitial));

		stubFor(get(urlMatching(PATH)).withQueryParam("watch", equalTo("false"))
			.withQueryParam("resourceVersion", equalTo("0"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.secret=true"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(mySecretListInitial))));

		// ------------------------------------------------------------------------------------------------------------
		// 1. first watch response to request with resourceVersion=1
		// color changed to blue

		V1Secret mySecretChanged = new V1Secret()
			.metadata(new V1ObjectMeta().namespace("default")
				.labels(Map.of("spring.cloud.kubernetes.secret", "true"))
				.name("my-secret")
				.resourceVersion("2"))
			.data(Map.of("color", "blue".getBytes(StandardCharsets.UTF_8)));

		Watch.Response<V1Secret> watchResponseOne = new Watch.Response<>(MODIFIED.name(), mySecretChanged);

		stubFor(get(urlMatching(PATH)).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("1"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.secret=true"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponseOne))));

		// ------------------------------------------------------------------------------------------------------------
		// 2. all future calls to informer ( any call with resourceVersion >= 2 )
		stubFor(get(urlMatching(PATH)).atPriority(10)
			.withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", WireMock.matching("[2-9][0-9]*"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.secret=true"))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		CoreV1Api coreV1Api() {
			return new CoreV1Api(new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build());
		}

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider namespaceProvider = Mockito.mock(KubernetesNamespaceProvider.class);
			Mockito.when(namespaceProvider.getNamespace()).thenReturn("default");
			return namespaceProvider;
		}

		@Bean
		HttpRefreshTrigger httpRefreshTrigger() {
			HttpRefreshTrigger refreshTrigger = Mockito.mock(HttpRefreshTrigger.class);
			Mockito.when(refreshTrigger.triggerRefresh(Mockito.any(), Mockito.anyString())).thenAnswer(invocation -> {
				V1Secret secret = invocation.getArgument(0);
				return Mono.fromRunnable(
						() -> OBSERVED_COLORS.add(new String(secret.getData().get("color"), StandardCharsets.UTF_8)));
			});

			return refreshTrigger;
		}

	}

}
