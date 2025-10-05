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

package org.springframework.cloud.kubernetes.fabric8.config.reload_it;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that
 * <a href="https://github.com/spring-cloud/spring-cloud-kubernetes/issues/2008">this</a>
 * issue is fixed.
 *
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.bootstrap.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.bootstrap.name=polling-reload-configmap-and-secret",
				"spring.main.cloud-platform=KUBERNETES", "spring.application.name=polling-reload-configmap-and-secret",
				"spring.main.allow-bean-definition-overriding=true",
				"spring.cloud.kubernetes.client.namespace=spring-k8s",
				"logging.level.org.springframework.cloud.kubernetes.commons.config.reload=debug",
				"spring.cloud.kubernetes.secrets.enabled=true" },
		classes = { PollingReloadConfigMapAndSecretTest.TestConfig.class, App.class })
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class PollingReloadConfigMapAndSecretTest {

	private static final String NAMESPACE = "spring-k8s";

	private static final AtomicBoolean STRATEGY_FOR_SECRET_CALLED = new AtomicBoolean(false);

	private static KubernetesClient mockClient;

	@Autowired
	private ConfigurableEnvironment environment;

	@BeforeAll
	static void beforeAll() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// namespace: spring-k8s, name: secret-a
		Map<String, String> secretA = Collections.singletonMap("one",
				Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8)));
		createSecret("secret-a", secretA);

		// namespace: spring-k8s, name: secret-b
		Map<String, String> secretB = Collections.singletonMap("two",
				Base64.getEncoder().encodeToString("b".getBytes(StandardCharsets.UTF_8)));
		createSecret("secret-b", secretB);

		// namespace: spring-k8s, name: configmap-a
		Map<String, String> configMapA = Collections.singletonMap("one", "a");
		createConfigMap("configmap-a", configMapA);

		// namespace: spring-k8s, name: configmap-b
		Map<String, String> configMapB = Collections.singletonMap("two", "b");
		createConfigMap("configmap-b", configMapB);

	}

	@Test
	void test(CapturedOutput output) {

		Set<String> sources = environment.getPropertySources()
			.stream()
			.map(PropertySource::getName)
			.collect(Collectors.toSet());
		assertThat(sources).contains("bootstrapProperties-configmap.configmap-b.spring-k8s",
				"bootstrapProperties-configmap.configmap-a.spring-k8s",
				"bootstrapProperties-secret.secret-b.spring-k8s", "bootstrapProperties-secret.secret-a.spring-k8s");

		// 1. first, wait for a cycle where we see the configmaps as being the same
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered"));

		// 2. then change a configmap, so the cycle seems them as different and triggers a
		// reload
		Map<String, String> configMapA = Collections.singletonMap("one", "aa");
		replaceConfigMap("configmap-a", configMapA);

		// 3. reload is triggered
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(STRATEGY_FOR_SECRET_CALLED::get);

	}

	private static void createSecret(String name, Map<String, String> data) {
		mockClient.secrets()
			.inNamespace(NAMESPACE)
			.resource(new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build())
			.create();
	}

	private static void createConfigMap(String name, Map<String, String> data) {
		mockClient.configMaps()
			.inNamespace(NAMESPACE)
			.resource(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build())
			.create();
	}

	private static void replaceConfigMap(String name, Map<String, String> data) {
		mockClient.configMaps()
			.inNamespace(NAMESPACE)
			.resource(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build())
			.createOrReplace();
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
		ConfigReloadProperties configReloadProperties() {
			return new ConfigReloadProperties(true, true, true, ConfigReloadProperties.ReloadStrategy.REFRESH,
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(200), Set.of(NAMESPACE),
					false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigurationUpdateStrategy secretConfigurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> STRATEGY_FOR_SECRET_CALLED.set(true));
		}

	}

}
