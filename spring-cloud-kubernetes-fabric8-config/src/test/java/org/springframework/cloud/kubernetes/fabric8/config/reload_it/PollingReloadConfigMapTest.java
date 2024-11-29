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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
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
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.VisibleFabric8ConfigMapPropertySourceLocator;
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
		classes = { PollingReloadConfigMapTest.TestConfig.class })
@EnableKubernetesMockClient
@ExtendWith(OutputCaptureExtension.class)
class PollingReloadConfigMapTest {

	private static final boolean FAIL_FAST = false;

	private static final String CONFIG_MAP_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static KubernetesMockServer kubernetesMockServer;

	private static KubernetesClient kubernetesClient;

	private static final boolean[] strategyCalled = new boolean[] { false };

	@BeforeAll
	static void beforeAll() {

		kubernetesClient.getConfiguration().setRequestRetryBackoffLimit(0);

		// needed so that our environment is populated with 'something'
		// this call is done in the method that returns the AbstractEnvironment
		ConfigMap configMapOne = configMap(CONFIG_MAP_NAME, Map.of());
		ConfigMap configMapTwo = configMap(CONFIG_MAP_NAME, Map.of("a", "b"));
		String path = "/api/v1/namespaces/spring-k8s/configmaps";
		kubernetesMockServer.expect()
			.withPath(path)
			.andReturn(200, new ConfigMapListBuilder().withItems(configMapOne).build())
			.once();

		kubernetesMockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		kubernetesMockServer.expect()
			.withPath(path)
			.andReturn(200, new ConfigMapListBuilder().withItems(configMapTwo).build())
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
		// we fail while reading 'configMapOne'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !strategyCalled[0];
			return one && two && three && updateStrategyNotCalled;
		});

		// it passes while reading 'configMapTwo'
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> strategyCalled[0]);
	}

	private static ConfigMap configMap(String name, Map<String, String> data) {
		return new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().withData(data).build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		PollingConfigMapChangeDetector pollingConfigMapChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator) {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			return new PollingConfigMapChangeDetector(environment, configReloadProperties, configurationUpdateStrategy,
					Fabric8ConfigMapPropertySource.class, fabric8ConfigMapPropertySourceLocator, scheduler);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a Fabric8ConfigMapPropertySource,
			// otherwise we can't properly test reload functionality
			ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
					List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE, false, true, true, RetryProperties.DEFAULT);
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
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(2000), Set.of("non-default"),
					false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigMapConfigProperties configMapConfigProperties() {
			return new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true, CONFIG_MAP_NAME, NAMESPACE,
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
		Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator(
				ConfigMapConfigProperties configMapConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new VisibleFabric8ConfigMapPropertySourceLocator(kubernetesClient, configMapConfigProperties,
					namespaceProvider);
		}

	}

}
