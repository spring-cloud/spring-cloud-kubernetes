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

package org.springframework.cloud.kubernetes.client.discovery.reactive;

import java.io.StringReader;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerSelectiveNamespacesAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
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
 * {@link KubernetesInformerReactiveDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesInformerReactiveDiscoveryClientAutoConfigurationApplicationContextTests {

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
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

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
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

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
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void discoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 3);
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
				"spring.cloud.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b,c");
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
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
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
			// only "simple" one from commons, as ours is not picked up
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			// only "simple" one from commons, as ours is not picked up
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	/**
	 * blocking is disabled, and it should not impact reactive in any way.
	 */
	@Test
	void blockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	/**
	 * blocking is disabled, and it should not impact reactive in any way.
	 */
	@Test
	void blockingDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	@Test
	void healthDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void healthDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	@Test
	void healthEnabledClassNotPresent() {
		setupWithFilteredClassLoader("org.springframework.boot.actuate.health.ReactiveHealthIndicator",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

	@Test
	void healthEnabledClassNotPresentWithSelectiveNamespaces() {
		setupWithFilteredClassLoader("org.springframework.boot.actuate.health.ReactiveHealthIndicator",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertNonSelectiveNamespacesBeansMissing(context);
			assertSelectiveNamespacesBeansPresent(context, 2);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						KubernetesInformerReactiveDiscoveryClientAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
						UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
						KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class,
						KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class))
				.withUserConfiguration(ApiClientConfig.class).withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(String name, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						KubernetesInformerReactiveDiscoveryClientAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
						UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
						KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class,
						KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class))
				.withUserConfiguration(ApiClientConfig.class).withClassLoader(new FilteredClassLoader(name))
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
