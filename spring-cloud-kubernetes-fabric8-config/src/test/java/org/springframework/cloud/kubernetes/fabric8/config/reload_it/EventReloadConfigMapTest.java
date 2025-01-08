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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
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
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8EventBasedConfigMapChangeDetector;
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
		classes = { EventReloadConfigMapTest.TestConfig.class })
@EnableKubernetesMockClient(crud = true)
@ExtendWith(OutputCaptureExtension.class)
public class EventReloadConfigMapTest {

	private static final boolean FAIL_FAST = false;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static KubernetesClient kubernetesClient;

	private static final AtomicBoolean STRATEGY_CALLED = new AtomicBoolean(false);

	@BeforeAll
	static void beforeAll() {

		kubernetesClient = Mockito.spy(kubernetesClient);
		kubernetesClient.getConfiguration().setRequestRetryBackoffLimit(0);

		ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of());

		// for the informer, when it starts
		kubernetesClient.configMaps().inNamespace(NAMESPACE).resource(configMapOne).create();
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	void test(CapturedOutput output) {

		// we need to create this one before mocking calls
		NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> operation = kubernetesClient.configMaps()
			.inNamespace(NAMESPACE);

		// makes sure that when 'onEvent' is triggered (because we added a config map)
		// the call to /api/v1/namespaces/spring-k8s/configmaps will fail with an
		// Exception
		MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mixedOperation = Mockito
			.mock(MixedOperation.class);
		NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockedOperation = Mockito
			.mock(NonNamespaceOperation.class);
		Mockito.when(kubernetesClient.configMaps()).thenReturn(mixedOperation);
		Mockito.when(mixedOperation.inNamespace(NAMESPACE)).thenReturn(mockedOperation);
		Mockito.when(mockedOperation.list()).thenThrow(new RuntimeException("failed in reading configmap"));

		// create a different configmap that triggers even based reloading.
		// the one we create, will trigger a call to
		// /api/v1/namespaces/spring-k8s/configmaps
		// that we mocked above to fail.
		ConfigMap configMapTwo = configMap("not" + CONFIG_MAP_NAME, Map.of("a", "b"));
		operation.resource(configMapTwo).create();

		// we fail while reading 'configMapTwo'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !STRATEGY_CALLED.get();
			return one && two && three && updateStrategyNotCalled;
		});

		// reset the mock and replace our configmap with some data, so that reload
		// is triggered
		Mockito.reset(kubernetesClient);
		ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of("a", "b"));
		operation.resource(configMapOne).replace();

		// it passes while reading 'configMapThatWillPass'
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(STRATEGY_CALLED::get);
	}

	private static ConfigMap configMap(String name, Map<String, String> data) {
		return new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().withData(data).build();
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
		AbstractEnvironment environment() {
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a Fabric8ConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT,
					false);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(mockEnvironment);

			PropertySource<?> propertySource = new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient,
					configMapConfigProperties, namespaceProvider)
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
				STRATEGY_CALLED.set(true);
			});
		}

		@Bean
		@Primary
		Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator(
				ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient, configMapConfigProperties,
					namespaceProvider);
		}

	}

}
