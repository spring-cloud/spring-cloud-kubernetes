/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import java.io.StringReader;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertNonSelectiveNamespacesBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertNonSelectiveNamespacesBeansPresent;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertSelectiveNamespacesBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertSelectiveNamespacesBeansPresent;

/**
 * Test various conditionals for
 * {@link KubernetesInformerDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesInformerDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	private static K3sContainer container;

	@AfterAll
	static void afterAll() {
		container.stop();
	}

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void discoveryEnabledDefaultWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 3);
		});
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void discoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void discoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 4);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissing() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissingWithSelectiveNamespaces() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d,e");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 5);
		});
	}

	/**
	 * reactive is disabled and should not impact blocking in any way
	 */
	@Test
	void reactiveDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	/**
	 * reactive is disabled and should not impact blocking in any way
	 */
	@Test
	void reactiveDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d,e");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 5);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesInformerDiscoveryClientAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
						KubernetesClientInformerAutoConfiguration.class,
						KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class))
				.withUserConfiguration(ApiClientConfig.class).withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(Class<?> cls, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesInformerDiscoveryClientAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
						KubernetesClientInformerAutoConfiguration.class,
						KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader(cls)).withUserConfiguration(ApiClientConfig.class)
				.withPropertyValues(properties);
	}

	@Configuration
	static class ApiClientConfig {

		@Bean
		@Primary
		ApiClient apiClient() throws Exception {
			container = Commons.container();
			container.start();

			return Config.fromConfig(new StringReader(container.getKubeConfigYaml()));
		}

	}

}
