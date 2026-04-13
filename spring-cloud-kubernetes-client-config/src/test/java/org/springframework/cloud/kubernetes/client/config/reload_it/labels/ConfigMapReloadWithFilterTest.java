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

package org.springframework.cloud.kubernetes.client.config.reload_it.labels;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.VisibleKubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ContextConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.main.allow-bean-definition-overriding=true" },
		classes = { ConfigMapReloadWithFilterTest.TestConfig.class })
@ContextConfiguration(initializers = ConfigMapReloadWithFilterTest.Initializer.class)
@ExtendWith(OutputCaptureExtension.class)
class ConfigMapReloadWithFilterTest {

	private static WireMockServer wireMockServer;

	private static final String PATH = "/api/v1/namespaces/spring-k8s/configmaps";

	private static final boolean FAIL_FAST = false;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

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
		MOCK_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient).thenReturn(client);
		MOCK_STATIC.when(() -> KubernetesClientUtils.getApplicationNamespace(
			Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(NAMESPACE);
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
	 *     - we enable reload filtering, via 'spring.cloud.kubernetes.reload.enable-reload-filtering=true'
	 *       ( this is done in ConfigReloadProperties )
	 *     - as such, only configmaps that have 'spring.cloud.kubernetes.config.informer.enabled=true'
	 *       label are being watched. This is what the informer is created with.
	 * </pre>
	 */
	@Test
	void test() throws InterruptedException {
		V1ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of("a", "b"),
				Map.of("spring.cloud.kubernetes.config.informer.enabled", "true"));

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMapOne);
		stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(JSON.serialize(configMapList))));

		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapOne);
		Awaitilities.awaitUntil(10, 1000, STRATEGY_CALLED::get);

		STRATEGY_CALLED.set(false);

		// create a configMap without label, the informer does not pick it up
		configMapOne = configMap(CONFIG_MAP_NAME, Map.of("c", "d"), Map.of());
		configMapList = new V1ConfigMapList().addItemsItem(configMapOne);
		stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(JSON.serialize(configMapList))));

		kubernetesClientEventBasedConfigMapChangeDetector.onEvent(configMapOne);
		Thread.sleep(3_000);
		assertThat(STRATEGY_CALLED.get()).isFalse();

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
		ConfigReloadProperties configReloadProperties() {

			boolean monitorConfigMaps = true;
			boolean monitorSecrets = false;
			boolean enableReloadFiltering = true;
			Map<String, String> configMapsLabels = Map.of();
			Map<String, String> secretsLabels = Map.of();

			return new ConfigReloadProperties(true, monitorConfigMaps, configMapsLabels, monitorSecrets, secretsLabels,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.POLLING,
				Duration.ofMillis(2000), Set.of("spring-k8s"), enableReloadFiltering, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigMapConfigProperties configMapConfigProperties() {
			return new ConfigMapConfigProperties(true, List.of(), Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true,
				FAIL_FAST, RetryProperties.DEFAULT, ReadType.BATCH);
		}

		@Bean
		@Primary
		KubernetesNamespaceProvider namespaceProvider(AbstractEnvironment environment) {
			return new KubernetesNamespaceProvider(environment);
		}

		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> STRATEGY_CALLED.set(true));
		}

		@Bean
		@Primary
		KubernetesClientConfigMapPropertySourceLocator kubernetesClientConfigMapPropertySourceLocator(
			ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new KubernetesClientConfigMapPropertySourceLocator(coreV1Api, configMapConfigProperties,
				namespaceProvider);
		}

	}

	private static V1ConfigMap configMap(String name, Map<String, String> data, Map<String, String> labels) {
		return new V1ConfigMapBuilder().withNewMetadata().withName(name).withLabels(labels)
			.endMetadata().withData(data).build();
	}

	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext context) {

			ConfigurableEnvironment environment = context.getEnvironment();

			V1ConfigMap configMap = configMap(CONFIG_MAP_NAME, Map.of(), Map.of());
			V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMap);

			// needed so that our environment is populated with 'something'
			// this call is done in the method that returns the AbstractEnvironment
			stubFor(get(PATH).willReturn(aResponse().withStatus(200).withBody(JSON.serialize(configMapList))));

			// simulate that environment already has a
			// KubernetesClientConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true, FAIL_FAST, RetryProperties.DEFAULT,
				ReadType.BATCH);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);

			PropertySource<?> propertySource = new KubernetesClientConfigMapPropertySourceLocator(coreV1Api,
				configMapConfigProperties, namespaceProvider)
				.locate(environment);

			environment.getPropertySources().addFirst(propertySource);

		}
	}

}
