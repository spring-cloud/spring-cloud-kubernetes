/*
 * Copyright 2013-present the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertInformerBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertInformerBeansPresent;

/**
 * Test various conditionals for
 * {@link KubernetesClientInformerDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesClientInformerDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	private static K3sContainer container;

	@AfterAll
	static void afterAll() {
		container.stop();
	}

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledDefaultWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			// 3 namespaces
			assertInformerBeansPresent(context, 3);
		});
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			// reactive only present
			assertThat(context).hasBean("reactiveIndicatorInitializer");
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");
		});
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansMissing(context);
		});
	}

	@Test
	void discoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 4);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
			// not ours, but form commons
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			// simpleReactiveDiscoveryClientHealthIndicator
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabledWithSelectiveNamespaces() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissing() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			// reactive only present
			assertThat(context).hasBean("reactiveIndicatorInitializer");
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissingWithSelectiveNamespaces() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d,e");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// ours
			assertThat(context).hasBean("kubernetesReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 5);
		});
	}

	/**
	 * reactive is disabled and should not impact blocking in any way
	 */
	@Test
	void reactiveDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansPresent(context, 1);
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
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansPresent(context, 5);
		});
	}

	/**
	 * <pre>
	 *     - no property related to cacheable in the blocking implementation is set, as such:
	 *     - KubernetesClientInformerDiscoveryClient is present
	 *     - KubernetesClientCacheableInformerDiscoveryClient is not present
	 * </pre>
	 */
	@Test
	void blockingCacheableDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientCacheableInformerDiscoveryClient.class);
		});
	}

	/**
	 * <pre>
	 *     - cacheable in the blocking implementation = false, as such:
	 *     - KubernetesClientInformerDiscoveryClient is present
	 *     - KubernetesClientCacheableInformerDiscoveryClient is not present
	 * </pre>
	 */
	@Test
	void blockingCacheableDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.cacheable.blocking.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientCacheableInformerDiscoveryClient.class);
		});
	}

	/**
	 * <pre>
	 *     - cacheable in the blocking implementation = true, as such:
	 *     - KubernetesClientInformerDiscoveryClient is not present
	 *     - KubernetesClientCacheableInformerDiscoveryClient is present
	 * </pre>
	 */
	@Test
	void blockingCacheableEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.cacheable.blocking.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientCacheableInformerDiscoveryClient.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
					UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesClientInformerAutoConfiguration.class,
					KubernetesClientInformerDiscoveryClientAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class,
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class))
			.withUserConfiguration(
					KubernetesClientInformerDiscoveryClientAutoConfigurationApplicationContextTests.ApiClientConfig.class)
			.withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(Class<?> cls, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
					UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class,
					KubernetesClientInformerDiscoveryClientAutoConfiguration.class,
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class))
			.withUserConfiguration(
					KubernetesClientInformerDiscoveryClientAutoConfigurationApplicationContextTests.ApiClientConfig.class)
			.withClassLoader(new FilteredClassLoader(cls))
			.withPropertyValues(properties);
	}

	@Configuration(proxyBeanMethods = false)
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
