/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.reload_it;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingSecretsChangeDetector;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8SecretsPropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true",
				"logging.level.org.springframework.cloud.kubernetes.commons.config=debug" },
		classes = { PollingReloadSecretTest.TestConfig.class })
@EnableKubernetesMockClient
@ExtendWith(OutputCaptureExtension.class)

public class PollingReloadSecretTest {

	private static final boolean FAIL_FAST = false;

	private static final String SECRET_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static KubernetesMockServer kubernetesMockServer;

	private static KubernetesClient kubernetesClient;

	private static final boolean[] strategyCalled = new boolean[] { false };

	@BeforeAll
	static void beforeAll() {

		kubernetesClient.getConfiguration().setRequestRetryBackoffLimit(0);

		// needed so that our environment is populated with 'something'
		// this call is done in the method that returns the AbstractEnvironment
		Secret secretOne = secret(SECRET_NAME, Map.of());
		Secret secretTwo = secret(SECRET_NAME, Map.of("a", "b"));
		String path = "/api/v1/namespaces/spring-k8s/secrets";
		kubernetesMockServer.expect()
			.withPath(path)
			.andReturn(200, new SecretListBuilder().withItems(secretOne).build())
			.once();

		kubernetesMockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		kubernetesMockServer.expect()
			.withPath(path)
			.andReturn(200, new SecretListBuilder().withItems(secretTwo).build())
			.once();
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
		// we fail while reading 'secretOne'
		Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !strategyCalled[0];
			return one && two && three && updateStrategyNotCalled;
		});

		// it passes while reading 'secretTwo'
		Awaitility.await()
			.atMost(Duration.ofSeconds(20))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> strategyCalled[0]);
	}

	private static Secret secret(String name, Map<String, String> data) {
		Map<String, String> encoded = data.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					e -> new String(Base64.getEncoder().encode(e.getValue().getBytes()))));
		return new SecretBuilder().withNewMetadata().withName(name).endMetadata().withData(encoded).build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		PollingSecretsChangeDetector pollingSecretsChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator) {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			return new PollingSecretsChangeDetector(environment, configReloadProperties, configurationUpdateStrategy,
					Fabric8SecretsPropertySource.class, fabric8SecretsPropertySourceLocator, scheduler);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a Fabric8SecretsPropertySource,
			// otherwise we can't properly test reload functionality
			SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
					List.of(), true, SECRET_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(mockEnvironment);

			PropertySource<?> propertySource = new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient,
					secretsConfigProperties, namespaceProvider)
				.locate(mockEnvironment);

			mockEnvironment.getPropertySources().addFirst(propertySource);
			return mockEnvironment;
		}

		@Bean
		@Primary
		ConfigReloadProperties configReloadProperties() {
			return new ConfigReloadProperties(true, true, true, ConfigReloadProperties.ReloadStrategy.REFRESH,
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(2000), Set.of("non-default"),
					false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		SecretsConfigProperties secretsConfigProperties() {
			return new SecretsConfigProperties(true, Map.of(), List.of(), List.of(), true, SECRET_NAME, NAMESPACE,
					false, true, FAIL_FAST, RetryProperties.DEFAULT);
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
		Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator(
				SecretsConfigProperties secretsConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8SecretsPropertySourceLocator(kubernetesClient, secretsConfigProperties,
					namespaceProvider);
		}

	}

}
