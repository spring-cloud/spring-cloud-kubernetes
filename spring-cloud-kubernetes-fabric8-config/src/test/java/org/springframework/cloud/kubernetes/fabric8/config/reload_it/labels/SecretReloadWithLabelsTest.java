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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedSecretsChangeDetector;
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
		classes = { SecretReloadWithLabelsTest.TestConfig.class })
@ContextConfiguration(initializers = SecretReloadWithLabelsTest.Initializer.class)
@EnableKubernetesMockClient(crud = true, https = false)
class SecretReloadWithLabelsTest {

	private static KubernetesClient kubernetesClient;

	private static final boolean FAIL_FAST = false;

	private static final String SECRET_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

	/**
	 * <pre>
	 *     - we only watch secrets with labels: { only-shape:round }
	 * </pre>
	 */
	@Test
	void test() throws InterruptedException {
		Secret secret = secret(SECRET_NAME, Map.of("a", "b"), Map.of("only-shape", "round"));

		kubernetesClient.secrets().inNamespace(NAMESPACE).resource(secret).create();
		Awaitilities.awaitUntil(10, 1000, STRATEGY_CALLED::get);
		kubernetesClient.secrets().inAnyNamespace().delete();
		Awaitilities.awaitUntil(10, 1000,
				() -> kubernetesClient.secrets().inNamespace(NAMESPACE).withName(SECRET_NAME).get() == null);

		// reset the strategy
		STRATEGY_CALLED.set(false);

		// create a secret without label, the informer does not pick it up
		secret = secret(SECRET_NAME, Map.of("c", "d"), Map.of());
		kubernetesClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		Awaitility.await()
			.pollDelay(Duration.ofSeconds(3))
			.atMost(Duration.ofSeconds(4))
			.pollInterval(Duration.ofMillis(100))
			.until(() -> !STRATEGY_CALLED.get());

	}

	private static Secret secret(String name, Map<String, String> data, Map<String, String> labels) {
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
		Fabric8EventBasedSecretsChangeDetector fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new Fabric8EventBasedSecretsChangeDetector(environment, configReloadProperties, kubernetesClient,
					configurationUpdateStrategy, fabric8SecretsPropertySourceLocator, namespaceProvider);
		}

		@Bean
		@Primary
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
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> STRATEGY_CALLED.set(true));
		}

		@Bean
		@Primary
		Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator(
				SecretsConfigProperties secretsConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient, secretsConfigProperties,
					namespaceProvider);
		}

	}

	/**
	 * This one is called before the context is refreshed.
	 */
	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			ConfigurableEnvironment environment = context.getEnvironment();

			// simulate that environment already has a Fabric8SecretsPropertySource,
			// otherwise we can't properly test reload functionality
			SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, List.of(), Map.of(),
					SECRET_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT, ReadType.BATCH);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);

			PropertySource<?> propertySource = new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient,
					secretsConfigProperties, namespaceProvider)
				.locate(environment);

			environment.getPropertySources().addFirst(propertySource);
		}

	}

}
