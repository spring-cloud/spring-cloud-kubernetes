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

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertInformerBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertInformerBeansPresent;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.mockEndpointsAndServices;

/**
 * Test various conditionals for
 * {@link KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesClientInformerReactiveDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@RegisterExtension
	private static final WireMockExtension API_SERVER = WireMockExtension.newInstance()
		.options(options().dynamicPort())
		.build();

	@AfterEach
	void afterEach() {
		API_SERVER.resetAll();
	}

	@Test
	void discoveryEnabledDefault() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledDefaultWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b", "c"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 3);
		});
	}

	@Test
	void discoveryEnabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b", "c"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 3);
		});
	}

	@Test
	void discoveryDisabled() {

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=false");
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

		mockEndpointsAndServices(List.of("a", "b", "c"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			// only "simple" one from commons, as ours is not picked up
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);

			assertInformerBeansMissing(context);
		});
	}

	@Test
	void kubernetesDiscoveryDisabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			// only "simple" one from commons, as ours is not picked up
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);

			// we don't read the KubernetesClientInformerAutoConfiguration
			assertInformerBeansMissing(context);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryEnabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.reactive.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.reactive.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.reactive.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryDisabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.reactive.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 2);
		});
	}

	/**
	 * blocking is disabled, and it should not impact reactive in any way.
	 */
	@Test
	void blockingDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	/**
	 * blocking is disabled, and it should not impact reactive in any way.
	 */
	@Test
	void blockingDisabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void healthDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void healthDisabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void healthEnabledClassNotPresent() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setupWithFilteredClassLoader("org.springframework.boot.health.contributor.ReactiveHealthIndicator",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void healthEnabledClassNotPresentWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setupWithFilteredClassLoader("org.springframework.boot.health.contributor.ReactiveHealthIndicator",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);

			assertInformerBeansPresent(context, 2);
		});
	}

	/**
	 * <pre>
	 *     - no property related to cacheable in the reactive implementation is set, as such:
	 *     - KubernetesClientInformerReactiveDiscoveryClient is present
	 *     - KubernetesClientCacheableInformerReactiveDiscoveryClient is not present
	 * </pre>
	 */
	@Test
	void reactiveCacheableDefault() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientCacheableInformerReactiveDiscoveryClient.class);
		});
	}

	/**
	 * <pre>
	 *     - cacheable in the reactive implementation = false, as such:
	 *     - KubernetesClientInformerReactiveDiscoveryClient is present
	 *     - KubernetesClientCacheableInformerReactiveDiscoveryClient is not present
	 * </pre>
	 */
	@Test
	void reactiveCacheableDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.cacheable.reactive.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientCacheableInformerReactiveDiscoveryClient.class);
		});
	}

	/**
	 * <pre>
	 *     - cacheable in the reactive implementation = true, as such:
	 *     - KubernetesClientInformerReactiveDiscoveryClient is not present
	 *     - KubernetesClientCacheableInformerReactiveDiscoveryClient is present
	 *     - Health indicator is disabled
	 * </pre>
	 */
	@Test
	void reactiveCacheableEnabledWithoutHealthIndicator() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.cacheable.reactive.enabled=true",
				"spring.cloud.discovery.client.health-indicator.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientCacheableInformerReactiveDiscoveryClient.class);
		});
	}

	/**
	 * <pre>
	 *     - cacheable in the reactive implementation = true, as such:
	 *     - KubernetesClientInformerReactiveDiscoveryClient is not present
	 *     - KubernetesClientCacheableInformerReactiveDiscoveryClient is present
	 *     - Health indicator is enabled
	 * </pre>
	 */
	@Test
	void reactiveCacheableEnabledWithHealthIndicator() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.cacheable.reactive.enabled=true",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientCacheableInformerReactiveDiscoveryClient.class);
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
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class,
					KubernetesClientInformerReactiveHealthAutoConfiguration.class))
			.withUserConfiguration(ApiClientConfig.class)
			.withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(String name, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
					UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class,
					KubernetesClientInformerDiscoveryClientAutoConfiguration.class,
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class,
					KubernetesClientInformerReactiveHealthAutoConfiguration.class))
			.withUserConfiguration(ApiClientConfig.class)
			.withClassLoader(new FilteredClassLoader(name))
			.withPropertyValues(properties);
	}

	@Configuration
	static class ApiClientConfig {

		@Bean
		@Primary
		ApiClient apiClient() throws Exception {
			return new ClientBuilder().setBasePath("http://localhost:" + API_SERVER.getPort()).build();
		}

	}

}
