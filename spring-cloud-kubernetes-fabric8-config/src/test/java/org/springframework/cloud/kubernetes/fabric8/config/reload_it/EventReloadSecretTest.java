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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedSecretsChangeDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true",
				"logging.level.org.springframework.cloud.kubernetes.commons.config=debug" },
		classes = { EventReloadSecretTest.TestConfig.class })
@EnableKubernetesMockClient(crud = true)
@ExtendWith(OutputCaptureExtension.class)

class EventReloadSecretTest {

	private static final boolean FAIL_FAST = false;

	private static final String SECRET_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static KubernetesClient kubernetesClient;

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

	@BeforeAll
	static void beforeAll() {

		kubernetesClient = Mockito.spy(kubernetesClient);
		kubernetesClient.getConfiguration().setRequestRetryBackoffLimit(0);

		Secret secretOne = secret(SECRET_NAME, Map.of());

		// for the informer, when it starts
		kubernetesClient.secrets().inNamespace(NAMESPACE).resource(secretOne).create();
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	void test(CapturedOutput output) {

		// we need to create this one before mocking calls
		NonNamespaceOperation<Secret, SecretList, Resource<Secret>> operation = kubernetesClient.secrets()
			.inNamespace(NAMESPACE);

		// makes sure that when 'onEvent' is triggered (because we added a config map)
		// the call to /api/v1/namespaces/spring-k8s/secrets will fail with an
		// Exception
		MixedOperation<Secret, SecretList, Resource<Secret>> mixedOperation = Mockito.mock(MixedOperation.class);
		NonNamespaceOperation<Secret, SecretList, Resource<Secret>> mockedOperation = Mockito
			.mock(NonNamespaceOperation.class);
		Mockito.when(kubernetesClient.secrets()).thenReturn(mixedOperation);
		Mockito.when(mixedOperation.inNamespace(NAMESPACE)).thenReturn(mockedOperation);
		Mockito.when(mockedOperation.list()).thenThrow(new RuntimeException("failed in reading secret"));

		// create a different secret that triggers even based reloading.
		// the one we create, will trigger a call to
		// /api/v1/namespaces/spring-k8s/secrets
		// that we mocked above to fail.
		Secret secretTwo = secret("not" + SECRET_NAME, Map.of("a", "b"));
		operation.resource(secretTwo).create();

		// we fail while reading 'secretTwo'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !STRATEGY_CALLED.get();
			return one && two && three && updateStrategyNotCalled;
		});

		// reset the mock and replace our secret with some data, so that reload
		// is triggered
		Mockito.reset(kubernetesClient);
		Secret secretOne = secret(SECRET_NAME, Map.of("a", "b"));
		operation.resource(secretOne).replace();

		// it passes while reading 'secretOne'
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(STRATEGY_CALLED::get);
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
		Fabric8EventBasedSecretsChangeDetector fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
				KubernetesNamespaceProvider namespaceProvider) {
			return new Fabric8EventBasedSecretsChangeDetector(environment, configReloadProperties, kubernetesClient,
					configurationUpdateStrategy, fabric8SecretsPropertySourceLocator, namespaceProvider);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a
			// Fabric8SecretsPropertySourceLocator,
			// otherwise we can't properly test reload functionality
			SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
					true, SECRET_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT);
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
			return new ConfigReloadProperties(true, true, false, ConfigReloadProperties.ReloadStrategy.REFRESH,
					ConfigReloadProperties.ReloadDetectionMode.EVENT, Duration.ofMillis(2000), Set.of(NAMESPACE), false,
					Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		SecretsConfigProperties secretsConfigProperties() {
			return new SecretsConfigProperties(true, Map.of(), List.of(), true, SECRET_NAME, NAMESPACE,
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
				STRATEGY_CALLED.set(true);
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
