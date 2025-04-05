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

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true",
				"logging.level.org.springframework.cloud.kubernetes.commons.config=debug" },
		classes = { PollingReloadConfigMapTest.TestConfig.class })
@ExtendWith(OutputCaptureExtension.class)
class PollingReloadConfigMapTest {

	private static final boolean FAIL_FAST = false;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String PATH = "/api/v1/namespaces/spring-k8s/configmaps";

	private static final String NAMESPACE = "spring-k8s";

	private static final String SCENARIO_NAME = "reload-test";

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

	private static CoreV1Api coreV1Api;

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
		coreV1Api = new CoreV1Api();
	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	/**
	 * <pre>
	 *     - we have a PropertySource in the environment
	 *     - first polling cycle tries to read the sources from k8s and fails
	 *     - second polling cycle reads sources from k8s and finds a change
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		// first reload call fails
		stubFor(get(PATH).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario(SCENARIO_NAME)
			.whenScenarioStateIs("go-to-fail")
			.willSetStateTo("go-to-ok"));

		// we fail while reading 'configMapOne'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !STRATEGY_CALLED.get();
			System.out.println("one: " + one + " two: " + two + " three: " + three + " updateStrategyNotCalled: "
					+ updateStrategyNotCalled);
			return one && two && three && updateStrategyNotCalled;
		});

		// second reload call passes
		V1ConfigMap configMapTwo = configMap(CONFIG_MAP_NAME, Map.of("a", "b"));
		V1ConfigMapList listTwo = new V1ConfigMapList().addItemsItem(configMapTwo);
		stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listTwo)))
			.inScenario(SCENARIO_NAME)
			.whenScenarioStateIs("go-to-ok")
			.willSetStateTo("done"));

		System.out.println("first assertion passed");

		// it passes while reading 'configMapTwo'
		Awaitility.await()
			.atMost(Duration.ofSeconds(20))
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
		PollingConfigMapChangeDetector pollingConfigMapChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				KubernetesClientConfigMapPropertySourceLocator kubernetesClientConfigMapPropertySourceLocator) {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			return new PollingConfigMapChangeDetector(environment, configReloadProperties, configurationUpdateStrategy,
					KubernetesClientConfigMapPropertySource.class, kubernetesClientConfigMapPropertySourceLocator,
					scheduler);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {

			V1ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of());
			V1ConfigMapList listOne = new V1ConfigMapList().addItemsItem(configMapOne);

			// needed so that our environment is populated with 'something'
			// this call is done in the method that returns the AbstractEnvironment
			stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listOne)))
				.inScenario(SCENARIO_NAME)
				.whenScenarioStateIs(Scenario.STARTED)
				.willSetStateTo("go-to-fail"));

			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a
			// KubernetesClientConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT,
					true);
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
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(2000), Set.of("non-default"),
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
