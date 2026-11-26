/*
 * Copyright 2012-present the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.kubernetes.client.informer.EventType.ADDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.getApplicationNamespace;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.main.allow-bean-definition-overriding=true" })
@ExtendWith(OutputCaptureExtension.class)
class ConfigMapReloadWithFilterTest {

	private static WireMockServer wireMockServer;

	private static final String PATH = "^/api/v1/namespaces/default/configmaps.*";

	private static CoreV1Api coreV1Api;

	private static final MockedStatic<KubernetesClientUtils> MOCK_STATIC = Mockito
		.mockStatic(KubernetesClientUtils.class);

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		MOCK_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient).thenReturn(client);
		MOCK_STATIC.when(() -> getApplicationNamespace(Mockito.nullable(String.class), anyString(), any()))
			.thenReturn("default");
		coreV1Api = new CoreV1Api(client);
	}

	@AfterAll
	static void after() {
		MOCK_STATIC.close();
		wireMockServer.stop();
	}

	@Test
	void test() {

		V1ConfigMap myConfigMap = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default")
				.name("my-config-map")
				.labels(Map.of("spring.cloud.kubernetes.config.informer.enabled", "true")))
			.data(Map.of("shape", "round"));

		V1ConfigMapList configMapList = new V1ConfigMapList().metadata(new V1ListMeta().resourceVersion("1"))
			.items(List.of(myConfigMap));

		// 1. first call to informer
		stubFor(get(urlMatching(PATH)).withQueryParam("watch", equalTo("false"))
			.withQueryParam("resourceVersion", equalTo("0"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.config.informer.enabled=true"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(configMapList))));

		// 2. second call to informer
		V1ConfigMap second = new V1ConfigMap()
			.metadata(new V1ObjectMeta().namespace("default")
				.name("second")
				.labels(Map.of("spring.cloud.kubernetes.config.informer.enabled", "true"))
				.resourceVersion("2"))
			.data(Map.of("a", "b"));
		Watch.Response<V1ConfigMap> watchResponse = new Watch.Response<>(ADDED.name(), second);

		stubFor(get(urlMatching(PATH)).withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", equalTo("1"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.config.informer.enabled=true"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(watchResponse))));

		// 3. all future calls to informer ( any call with resourceVersion >= 2 )
		stubFor(get(urlMatching(PATH)).atPriority(10)
			.withQueryParam("watch", equalTo("true"))
			.withQueryParam("resourceVersion", WireMock.matching("[2-9][0-9]*"))
			.withQueryParam("labelSelector", equalTo("spring.cloud.kubernetes.config.informer.enabled=true"))
			.willReturn(aResponse().withStatus(200).withBody("")));

		// update strategy
		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("strategy", () -> {

		});

		// mock environment
		KubernetesMockEnvironment environment = new KubernetesMockEnvironment(
				mock(KubernetesClientConfigMapPropertySource.class));

		// change locator
		KubernetesClientConfigMapPropertySourceLocator locator = mock(
				KubernetesClientConfigMapPropertySourceLocator.class);
		when(locator.locate(environment)).thenAnswer(x -> new MockPropertySource());

		// namespace provider
		KubernetesNamespaceProvider namespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(namespaceProvider.getNamespace()).thenReturn("default");

		// properties
		boolean enableReloadFiltering = true;
		boolean monitorConfigMaps = true;
		boolean monitorSecrets = false;
		Map<String, String> configMapsLabels = Map.of();
		Map<String, String> secretsLabels = Map.of();
		ConfigReloadProperties properties = new ConfigReloadProperties(true, monitorConfigMaps, configMapsLabels,
				monitorSecrets, secretsLabels, ConfigReloadProperties.ReloadStrategy.REFRESH,
				ConfigReloadProperties.ReloadDetectionMode.EVENT, Duration.ofMillis(15000), Set.of(),
				enableReloadFiltering, Duration.ofSeconds(2));

		// change detector
		KubernetesClientEventBasedConfigMapChangeDetector changeDetector = new KubernetesClientEventBasedConfigMapChangeDetector(
				coreV1Api, environment, properties, strategy, locator, namespaceProvider);

		changeDetector.inform();

		// assert that both requests from informer are label based
		Awaitilities.awaitUntil(10, 1000, () -> {
			try {

				WireMock.verify(WireMock.moreThanOrExactly(1),
						getRequestedFor(urlMatching(PATH)).withQueryParam("watch", equalTo("false"))
							.withQueryParam("labelSelector",
									equalTo("spring.cloud.kubernetes.config.informer.enabled=true")));

				WireMock.verify(WireMock.moreThanOrExactly(1),
						getRequestedFor(urlMatching(PATH)).withQueryParam("watch", equalTo("true"))
							.withQueryParam("labelSelector",
									equalTo("spring.cloud.kubernetes.config.informer.enabled=true")));

				WireMock.verify(0, getRequestedFor(urlMatching(PATH)).withQueryParam("watch", equalTo("false"))
					.withoutQueryParam("labelSelector"));

				WireMock.verify(0, getRequestedFor(urlMatching(PATH)).withQueryParam("watch", equalTo("true"))
					.withoutQueryParam("labelSelector"));

				return true;
			}
			catch (VerificationException e) {
				return false;
			}
		});

		changeDetector.shutdown();

		assertThat(wireMockServer.findAllUnmatchedRequests()).isEmpty();

	}

}
