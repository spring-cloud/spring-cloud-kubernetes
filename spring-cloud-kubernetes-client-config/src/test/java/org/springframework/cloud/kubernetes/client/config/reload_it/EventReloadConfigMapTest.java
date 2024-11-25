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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
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

	private static final boolean[] strategyCalled = new boolean[] { false };

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

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		MOCK_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient).thenReturn(client);
		MOCK_STATIC
			.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.anyString(), Mockito.anyString(),
					Mockito.any()))
			.thenReturn(NAMESPACE);
		Configuration.setDefaultApiClient(client);
		coreV1Api = new CoreV1Api();

		String path = "/api/v1/namespaces/spring-k8s/configmaps";
		V1ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of());
		V1ConfigMapList listOne = new V1ConfigMapList().addItemsItem(configMapOne);

		// needed so that our environment is populated with 'something'
		// this call is done in the method that returns the AbstractEnvironment
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listOne)))
			.inScenario("mine-test")
			.willSetStateTo("go-to-fail"));

		// first call will fail
		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("mine-test")
			.whenScenarioStateIs("go-to-fail")
			.willSetStateTo("go-to-ok"));

		// second call passes (change data so that reload is triggered)
		configMapOne = configMap(CONFIG_MAP_NAME, Map.of("a", "b"));
		listOne = new V1ConfigMapList().addItemsItem(configMapOne);
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listOne)))
			.inScenario("mine-test")
			.whenScenarioStateIs("go-to-ok")
			.willSetStateTo("done"));
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
		V1ConfigMap configMapNotMine = configMap("not" + CONFIG_MAP_NAME, Map.of());
		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapNotMine);

		// we fail while reading 'configMapOne'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("failure in reading named sources");
			boolean two = output.getOut()
				.contains("there was an error while reading config maps/secrets, no reload will happen");
			boolean three = output.getOut()
				.contains("reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !strategyCalled[0];
			return one && two && three && updateStrategyNotCalled;
		});

		// trigger the call again
		V1ConfigMap configMapMine = configMap(CONFIG_MAP_NAME, Map.of());
		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapMine);
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> strategyCalled[0]);
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
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a
			// KubernetesClientConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE, false, true, FAIL_FAST,
					RetryProperties.DEFAULT, false);
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
					false, true, FAIL_FAST, RetryProperties.DEFAULT, false);
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
				strategyCalled[0] = true;
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
