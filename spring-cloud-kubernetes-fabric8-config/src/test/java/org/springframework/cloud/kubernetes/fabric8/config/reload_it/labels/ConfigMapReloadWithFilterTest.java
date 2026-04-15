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

package org.springframework.cloud.kubernetes.fabric8.config.reload_it.labels;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.main.allow-bean-definition-overriding=true" },
		classes = { ConfigMapReloadWithFilterTest.TestConfig.class })
@ContextConfiguration(initializers = ConfigMapReloadWithFilterTest.Initializer.class)
@EnableKubernetesMockClient(crud = true, https = false)
class ConfigMapReloadWithFilterTest {

	private static KubernetesClient kubernetesClient;

	private static final boolean FAIL_FAST = false;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

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
		ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of("a", "b"),
				Map.of("spring.cloud.kubernetes.config.informer.enabled", "true"));

		kubernetesClient.configMaps().inNamespace(NAMESPACE).resource(configMapOne).create();
		Awaitilities.awaitUntil(10, 1000, STRATEGY_CALLED::get);
		kubernetesClient.configMaps().inAnyNamespace().delete();
		Awaitilities.awaitUntil(10, 1000,
				() -> kubernetesClient.configMaps().inNamespace(NAMESPACE).withName(CONFIG_MAP_NAME).get() == null);

		// reset the strategy
		STRATEGY_CALLED.set(false);

		// create a configMap without label, the informer does not pick it up
		configMapOne = configMap(CONFIG_MAP_NAME, Map.of("c", "d"), Map.of());
		kubernetesClient.configMaps().inNamespace(NAMESPACE).resource(configMapOne).create();

		Awaitility.await()
			.pollDelay(Duration.ofSeconds(3))
			.atMost(Duration.ofSeconds(4))
			.pollInterval(Duration.ofMillis(100))
			.until(() -> !STRATEGY_CALLED.get());

	}

	private static ConfigMap configMap(String name, Map<String, String> data, Map<String, String> labels) {
		return new ConfigMapBuilder().withNewMetadata()
			.withResourceVersion("1")
			.withLabels(labels)
			.withName(name)
			.endMetadata()
			.withData(data)
			.build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		Fabric8EventBasedConfigMapChangeDetector fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new Fabric8EventBasedConfigMapChangeDetector(environment, configReloadProperties, kubernetesClient,
					configurationUpdateStrategy, fabric8ConfigMapPropertySourceLocator, namespaceProvider);
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
					ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
					Duration.ofMillis(2000), Set.of(NAMESPACE), enableReloadFiltering, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigMapConfigProperties configMapConfigProperties() {
			return new ConfigMapConfigProperties(true, List.of(), Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true,
					FAIL_FAST, RetryProperties.DEFAULT, ReadType.SINGLE);
		}

		@Bean
		@Primary
		KubernetesNamespaceProvider namespaceProvider(AbstractEnvironment environment) {
			return new KubernetesNamespaceProvider(environment);
		}

		// this is called by reloadProperties() and we simulated that
		// informer correctly caught the update for the configmap.
		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> STRATEGY_CALLED.set(true));
		}

		@Bean
		@Primary
		Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator(
				ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient, configMapConfigProperties,
					namespaceProvider);
		}

	}

	/**
	 * we need a bean of type 'Fabric8ConfigMapPropertySourceLocator' in the context
	 * ('VisibleFabric8ConfigMapPropertySourceLocator'), otherwise the reload will not
	 * work. This one is called before the context is refreshed.
	 */
	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			ConfigurableEnvironment environment = context.getEnvironment();

			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT, ReadType.SINGLE);

			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);

			PropertySource<?> propertySource = new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient,
					configMapConfigProperties, namespaceProvider)
				.locate(environment);

			environment.getPropertySources().addFirst(propertySource);
		}

	}

}
