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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
 * {@link KubernetesClientInformerDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesClientInformerDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@RegisterExtension
	private static final WireMockExtension API_SERVER = WireMockExtension.newInstance()
		.options(options().dynamicPort())
		.build();

	@AfterAll
	static void afterAll() {
		API_SERVER.shutdownServer();
	}

	@AfterEach
	void afterEach() {
		API_SERVER.resetAll();
	}

	@Test
	void discoveryEnabledDefault() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledDefaultWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b", "c"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			// 3 namespaces
			assertInformerBeansPresent(context, 3);
		});
	}

	@Test
	void discoveryEnabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void discoveryEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			// reactive only present
			assertThat(context).hasBean("reactiveIndicatorInitializer");
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
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

		mockEndpointsAndServices(List.of("a", "b", "c", "d"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// one from reactive, one from ours
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// one from blocking, one from reactive
			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 2);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=b");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).getBeans(KubernetesDiscoveryClientHealthIndicatorInitializer.class).hasSize(2);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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

		mockEndpointsAndServices(List.of("b"), API_SERVER);

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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 1);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissingWithSelectiveNamespaces() {

		mockEndpointsAndServices(List.of("a", "b", "c", "d", "e"), API_SERVER);

		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b,c,d,e");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and non-cacheable is the default option
			assertThat(context).hasBean("nonCacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 5);
		});
	}

	/**
	 * <pre>
	 *     -
	 * </pre>
	 */
	@Test
	void kubernetesDiscoveryReactiveCacheableEnabledBlockingDisabled() {

		mockEndpointsAndServices(List.of("a", "b"), API_SERVER);

		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.kubernetes.discovery.cacheable.reactive.enabled=true",
				"spring.cloud.kubernetes.discovery.namespaces=a,b");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			// cacheable client is present
			assertThat(context).hasSingleBean(KubernetesClientCacheableInformerReactiveDiscoveryClient.class);

			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			// reactive is enabled and cacheable is selected
			assertThat(context).hasBean("cacheableReactiveDiscoveryClientHealthIndicator");
			// from commons, not ours
			assertThat(context).hasBean("simpleReactiveDiscoveryClientHealthIndicator");

			assertInformerBeansPresent(context, 2);
		});
	}

	/**
	 * reactive is disabled and should not impact blocking in any way
	 */
	@Test
	void reactiveDisabled() {

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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

		mockEndpointsAndServices(List.of("a", "b", "c", "d", "e"), API_SERVER);

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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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

		mockEndpointsAndServices(List.of("default"), API_SERVER);

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
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class,
					KubernetesClientInformerReactiveHealthAutoConfiguration.class))
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
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class,
					KubernetesClientInformerReactiveHealthAutoConfiguration.class))
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
			return new ClientBuilder().setBasePath("http://localhost:" + API_SERVER.getPort()).build();
		}

	}

}
