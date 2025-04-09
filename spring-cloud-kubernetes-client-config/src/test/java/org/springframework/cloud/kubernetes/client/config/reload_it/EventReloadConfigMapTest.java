/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.reload_it;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.ClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.VisibleKubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true",
				"logging.level.org.springframework.cloud.kubernetes.commons.config=debug" },
		classes = { EventReloadConfigMapTest.TestConfig.class })
@ExtendWith(OutputCaptureExtension.class)
class EventReloadConfigMapTest {

	private static final boolean FAIL_FAST = false;

	private static WireMockServer wireMockServer;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static final String SCENARIO_NAME = "reload-test";

	private static final String PATH = "/api/v1/namespaces/spring-k8s/configmaps";

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

	private static CoreV1Api coreV1Api;

	private static final MockedStatic<KubernetesClientUtils> MOCK_STATIC = Mockito
		.mockStatic(KubernetesClientUtils.class);

	@Autowired
	private VisibleKubernetesClientEventBasedConfigMapChangeDetector kubernetesClientEventBasedConfigMapChangeDetector;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		// something that the informer can work with. Since we do not care about this one
		// in the test, we mock it to return a 500 as it does not matter anyway.
		stubFor(get(urlPathMatching(PATH)).withQueryParam("resourceVersion", equalTo("0"))
			.withQueryParam("watch", equalTo("false"))
			.willReturn(aResponse().withStatus(500).withBody("Error From Informer")));

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		MOCK_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient).thenReturn(client);
		MOCK_STATIC
			.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.anyString(), Mockito.anyString(),
					Mockito.any()))
			.thenReturn(NAMESPACE);
		Configuration.setDefaultApiClient(client);
		coreV1Api = new CoreV1Api();
	}

	@AfterAll
	static void after() {
		MOCK_STATIC.close();
		wireMockServer.stop();
	}

	/**
	 * <pre>
	 * 	- 'configmap.mine.spring-k8s' already exists in the environment
	 * 	-  we simulate that another configmap is created, so a request goes to k8s to find any potential
	 * 	   differences. This request is mocked to fail.
	 * 	- then our configmap is changed and the request passes
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		// first call will fail
		stubFor(get(PATH).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario(SCENARIO_NAME)
			.whenScenarioStateIs("go-to-fail")
			.willSetStateTo("go-to-ok"));

		V1ConfigMap configMapNotMine = configMap("not" + CONFIG_MAP_NAME, Map.of());
		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapNotMine);

		// we fail while reading 'configMapOne'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !STRATEGY_CALLED.get();
			return one && two && three && updateStrategyNotCalled;
		});

		// second call passes (change data so that reload is triggered)
		V1ConfigMap configMap = configMap(CONFIG_MAP_NAME, Map.of("a", "b"));
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMap);
		stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList)))
			.inScenario(SCENARIO_NAME)
			.whenScenarioStateIs("go-to-ok")
			.willSetStateTo("done"));

		// trigger the call again
		V1ConfigMap configMapMine = configMap(CONFIG_MAP_NAME, Map.of());
		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapMine);
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(STRATEGY_CALLED::get);
	}

	private static V1ConfigMap configMap(String name, Map<String, String> data) {
		return new V1ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().withData(data).build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		VisibleKubernetesClientEventBasedConfigMapChangeDetector kubernetesClientEventBasedConfigMapChangeDetector(
				AbstractEnvironment environment, ConfigReloadProperties configReloadProperties,
				ConfigurationUpdateStrategy configurationUpdateStrategy,
				KubernetesClientConfigMapPropertySourceLocator kubernetesClientConfigMapPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleKubernetesClientEventBasedConfigMapChangeDetector(coreV1Api, environment,
					configReloadProperties, configurationUpdateStrategy, kubernetesClientConfigMapPropertySourceLocator,
					namespaceProvider);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {

			V1ConfigMap configMap = configMap(CONFIG_MAP_NAME, Map.of());
			V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMap);

			// needed so that our environment is populated with 'something'
			// this call is done in the method that returns the AbstractEnvironment
			stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList)))
				.inScenario(SCENARIO_NAME)
				.whenScenarioStateIs(Scenario.STARTED)
				.willSetStateTo("go-to-fail"));

			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a
			// KubernetesClientConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE, false, true, FAIL_FAST,
					RetryProperties.DEFAULT, true);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(mockEnvironment);

			PropertySource<?> propertySource = new KubernetesClientConfigMapPropertySourceLocator(coreV1Api,
					configMapConfigProperties, namespaceProvider)
				.locate(mockEnvironment);

			mockEnvironment.getPropertySources().addFirst(propertySource);
			return mockEnvironment;
		}

		@Bean
		@Primary
		ConfigReloadProperties configReloadProperties() {
			return new ConfigReloadProperties(true, true, false, ConfigReloadProperties.ReloadStrategy.REFRESH,
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(2000), Set.of("spring-k8s"),
					false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigMapConfigProperties configMapConfigProperties() {
			return new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE,
					false, true, FAIL_FAST, RetryProperties.DEFAULT, true);
		}

		@Bean
		@Primary
		KubernetesNamespaceProvider namespaceProvider(AbstractEnvironment environment) {
			return new KubernetesNamespaceProvider(environment);
		}

		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> {
				STRATEGY_CALLED.set(true);
			});
		}

		@Bean
		@Primary
		KubernetesClientConfigMapPropertySourceLocator kubernetesClientConfigMapPropertySourceLocator(
				ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new KubernetesClientConfigMapPropertySourceLocator(coreV1Api, configMapConfigProperties,
					namespaceProvider);
		}

	}

}
