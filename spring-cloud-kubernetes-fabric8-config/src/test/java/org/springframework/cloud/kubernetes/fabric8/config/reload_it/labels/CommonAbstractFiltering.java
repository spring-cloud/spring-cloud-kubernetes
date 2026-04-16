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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedSecretsChangeDetector;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
abstract class CommonAbstractFiltering {

	@Autowired
	protected ReloadProbe reloadProbe;

	protected static KubernetesClient kubernetesClient;

	protected static final String CONFIG_MAP_NAME = "configmap-reload";

	protected static final String SECRET_NAME = "secret-reload";

	protected static final String NAMESPACE = "spring-k8s";

	private static final boolean FAIL_FAST = false;

	@AfterEach
	protected void afterEach() {
		kubernetesClient.configMaps().inAnyNamespace().delete();
		kubernetesClient.secrets().inAnyNamespace().delete();

		awaitUntil(10, 1000,
				() -> kubernetesClient.configMaps().inNamespace(NAMESPACE).withName(CONFIG_MAP_NAME).get() == null);
		awaitUntil(10, 1000,
				() -> kubernetesClient.secrets().inNamespace(NAMESPACE).withName(SECRET_NAME).get() == null);

		reloadProbe.reset();
	}

	protected static ConfigMap configMap(String name, Map<String, String> data, Map<String, String> labels) {
		return new ConfigMapBuilder().withNewMetadata()
			.withResourceVersion("1")
			.withLabels(labels)
			.withName(name)
			.endMetadata()
			.withData(data)
			.build();
	}

	protected static Secret secret(String name, Map<String, String> data, Map<String, String> labels) {
		Map<String, String> encoded = data.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					e -> new String(Base64.getEncoder().encode(e.getValue().getBytes()))));
		return new SecretBuilder().withNewMetadata()
			.withLabels(labels)
			.withName(name)
			.endMetadata()
			.withData(encoded)
			.build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		Fabric8EventBasedConfigMapChangeDetector fabric8ConfigMapEventBasedSecretsChangeDetector(
				AbstractEnvironment environment, ConfigReloadProperties configReloadProperties,
				ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new Fabric8EventBasedConfigMapChangeDetector(environment, configReloadProperties, kubernetesClient,
					configurationUpdateStrategy, fabric8ConfigMapPropertySourceLocator, namespaceProvider);
		}

		@Bean
		@Primary
		Fabric8EventBasedSecretsChangeDetector fabric8SecretsEventBasedSecretsChangeDetector(
				AbstractEnvironment environment, ConfigReloadProperties configReloadProperties,
				ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new Fabric8EventBasedSecretsChangeDetector(environment, configReloadProperties, kubernetesClient,
					configurationUpdateStrategy, fabric8SecretsPropertySourceLocator, namespaceProvider);
		}

		@Bean
		@Primary
		ConfigMapConfigProperties configMapConfigProperties() {
			return new ConfigMapConfigProperties(true, List.of(), Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true,
					FAIL_FAST, RetryProperties.DEFAULT, ReadType.SINGLE);
		}

		@Bean
		@Primary
		SecretsConfigProperties secretsConfigProperties() {
			return new SecretsConfigProperties(true, List.of(), Map.of(), SECRET_NAME, NAMESPACE, false, true,
					FAIL_FAST, RetryProperties.DEFAULT, ReadType.SINGLE);
		}

		@Bean
		@Primary
		KubernetesNamespaceProvider namespaceProvider(AbstractEnvironment environment) {
			return new KubernetesNamespaceProvider(environment);
		}

		@Bean
		ReloadProbe reloadProbe() {
			return new ReloadProbe();
		}

		// this is called by reloadProperties() and we simulated that
		// informer correctly caught the update for the configmap.
		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy(ReloadProbe reloadProbe) {
			return new ConfigurationUpdateStrategy("to-console", reloadProbe::markCalled);
		}

		@Bean
		@Primary
		Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator(
				ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient, configMapConfigProperties,
					namespaceProvider);
		}

		@Bean
		@Primary
		Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator(
				SecretsConfigProperties secretsConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient, secretsConfigProperties,
					namespaceProvider);
		}

	}

	@TestConfiguration
	static class ConfigReloadPropertiesConfiguration {

		@Bean
		@Primary
		@ConditionalOnProperty(value = "configmaps.reload.filtering", havingValue = "true", matchIfMissing = false)
		ConfigReloadProperties configReloadPropertiesA() {

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
		@ConditionalOnProperty(value = "configmaps.labels.filtering", havingValue = "true", matchIfMissing = false)
		ConfigReloadProperties configReloadPropertiesB() {

			boolean monitorConfigMaps = true;
			boolean monitorSecrets = false;
			boolean enableReloadFiltering = false;
			Map<String, String> configMapsLabels = Map.of("only-shape", "round");
			Map<String, String> secretsLabels = Map.of();

			return new ConfigReloadProperties(true, monitorConfigMaps, configMapsLabels, monitorSecrets, secretsLabels,
					ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
					Duration.ofMillis(2000), Set.of(NAMESPACE), enableReloadFiltering, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "secrets.reload.filtering", havingValue = "true", matchIfMissing = false)
		ConfigReloadProperties configReloadPropertiesC() {

			boolean monitorConfigMaps = false;
			boolean monitorSecrets = true;
			boolean enableReloadFiltering = true;
			Map<String, String> configMapsLabels = Map.of();
			Map<String, String> secretsLabels = Map.of();

			return new ConfigReloadProperties(true, monitorConfigMaps, configMapsLabels, monitorSecrets, secretsLabels,
					ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
					Duration.ofMillis(2000), Set.of(NAMESPACE), enableReloadFiltering, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "secrets.labels.filtering", havingValue = "true", matchIfMissing = false)
		ConfigReloadProperties configReloadProperties() {

			boolean monitorConfigMaps = false;
			boolean monitorSecrets = true;
			boolean enableReloadFiltering = false;
			Map<String, String> configMapsLabels = Map.of();
			Map<String, String> secretsLabels = Map.of("only-shape", "round");

			return new ConfigReloadProperties(true, monitorConfigMaps, configMapsLabels, monitorSecrets, secretsLabels,
					ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
					Duration.ofMillis(2000), Set.of(NAMESPACE), enableReloadFiltering, Duration.ofSeconds(2));
		}

	}

	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			ConfigurableEnvironment environment = context.getEnvironment();

			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					Map.of(), CONFIG_MAP_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT, ReadType.SINGLE);
			SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, List.of(), Map.of(),
					SECRET_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT, ReadType.BATCH);

			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);

			PropertySource<?> configMapPropertySource = new VisibleFabric8ConfigMapPropertySourceLocator(
					kubernetesClient, configMapConfigProperties, namespaceProvider)
				.locate(environment);
			PropertySource<?> secretsPropertySource = new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient,
					secretsConfigProperties, namespaceProvider)
				.locate(environment);

			environment.getPropertySources().addFirst(configMapPropertySource);
			environment.getPropertySources().addFirst(secretsPropertySource);
		}

	}

	/**
	 * to be used to assert that ConfigurationUpdateStrategy was called.
	 */
	static final class ReloadProbe {

		private final AtomicBoolean called = new AtomicBoolean(false);

		void markCalled() {
			called.set(true);
		}

		boolean isCalled() {
			return called.get();
		}

		boolean isNotCalled() {
			return !called.get();
		}

		void reset() {
			called.set(false);
		}

	}

}
