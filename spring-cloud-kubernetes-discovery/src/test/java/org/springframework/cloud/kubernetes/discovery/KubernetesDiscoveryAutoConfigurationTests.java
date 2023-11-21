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

package org.springframework.cloud.kubernetes.discovery;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesDiscoveryAutoConfigurationTests {

	/**
	 * <pre>
	 *     '@ConditionalOnDiscoveryEnabled' is not matched, thus no beans are created
	 *     from either blocking or reactive configurations.
	 * </pre>
	 */
	@Test
	void discoveryDisabled() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     '@ConditionalOnKubernetesDiscoveryEnabled' is not matched, thus no beans are created
	 *     from either blocking or reactive configurations.
	 * </pre>
	 */
	@Test
	void kubernetesDiscoveryDisabled() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     '@ConditionalOnCloudPlatform' does not match 'KUBERNETES', thus no beans are created
	 *     from either blocking or reactive configurations.
	 * </pre>
	 */
	@Test
	void cloudPlatformDisabled() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=none");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     - reactive config is disabled, blocking is defaulted.
	 *     - WebClient class is not present
	 * </pre>
	 */
	@Test
	void reactiveDisabledBlockingEnabledWebClientPresent() {
		setupWithFilteredClassLoader(WebClient.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(RestTemplate.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).getBean("indicatorInitializer").isNotNull();

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     - reactive config is disabled, blocking is defaulted.
	 *     - WebClient class is present
	 * </pre>
	 */
	@Test
	void reactiveDisabledBlockingEnabledWebClientMissing() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=false", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);
			assertThat(context).getBean("indicatorInitializer").isNull();

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     - reactive config is disabled, blocking is defaulted.
	 *     - WebClient class is present
	 * </pre>
	 */
	@Test
	void reactiveDisabledBlockingEnabledWebClientMissingHealthIndicatorMissing() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(RestTemplate.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean("indicatorInitializer");

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     '@ConditionalOnDiscoveryHealthIndicatorEnabled' not matched.
	 * </pre>
	 */
	@Test
	void blockingHealthIndicatorDisabled() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=true",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(RestTemplate.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean("indicatorInitializer");

			assertThat(context).hasSingleBean(WebClient.Builder.class);
			assertThat(context).hasSingleBean(KubernetesReactiveDiscoveryClient.class);

		});
	}

	/**
	 * <pre>
	 *     - reactive config is enabled, blocking is defaulted.
	 *     - WebClient class is present
	 * </pre>
	 */
	@Test
	void reactiveEnabledBlockingEnabledWebClientPresent() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=true",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(RestTemplate.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).getBean("indicatorInitializer").isNotNull();

			assertThat(context).hasSingleBean(WebClient.Builder.class);
			assertThat(context).hasSingleBean(KubernetesReactiveDiscoveryClient.class);
			assertThat(context).getBean("kubernetesReactiveDiscoveryClientHealthIndicator").isNotNull();
		});
	}

	/**
	 * <pre>
	 *     - reactive config is enabled, blocking is defaulted.
	 *     - WebClient class is missing
	 * </pre>
	 */
	@Test
	void reactiveEnabledBlockingEnabledWebClientMissing() {
		setupWithFilteredClassLoader(WebClient.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=true",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(RestTemplate.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).getBean("indicatorInitializer").isNotNull();

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);
			assertThat(context).getBean("kubernetesReactiveDiscoveryClientHealthIndicator").isNull();
		});
	}

	/**
	 * <pre>
	 *     '@ConditionalOnBlockingDiscoveryEnabled' is not matched.
	 * </pre>
	 */
	@Test
	void testBlockingDisabled() {
		setupWithFilteredClassLoader(WebClient.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=true", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);
			assertThat(context).getBean("indicatorInitializer").isNull();

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(KubernetesReactiveDiscoveryClient.class);
			assertThat(context).getBean("kubernetesReactiveDiscoveryClientHealthIndicator").isNull();
		});
	}

	/**
	 * <pre>
	 *     - WebClient is on the classpath (this is asserted via the presence of beans that come
	 *       from the reactive auto-configuration)
	 *     - This has no impact of the creation of the blocking discovery client
	 * </pre>
	 */
	@Test
	void testFor1426Issue() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.discovery.reactive.enabled=true", "spring.cloud.discovery.blocking.enabled=true",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver");
		applicationContextRunner.run(context -> {
			// blocking client is present
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);

			// reactive client is present
			assertThat(context).hasSingleBean(KubernetesReactiveDiscoveryClient.class);
		});
	}

	private ApplicationContextRunner applicationContextRunner;

	private void setupWithFilteredClassLoader(Class<?> cls, String... properties) {

		if (cls != null) {
			applicationContextRunner = new ApplicationContextRunner()
					.withConfiguration(AutoConfigurations.of(KubernetesDiscoveryClientBlockingAutoConfiguration.class,
							KubernetesDiscoveryClientReactiveAutoConfiguration.class))
					.withClassLoader(new FilteredClassLoader(cls)).withPropertyValues(properties);
		}
		else {
			applicationContextRunner = new ApplicationContextRunner()
					.withConfiguration(AutoConfigurations.of(KubernetesDiscoveryClientBlockingAutoConfiguration.class,
							KubernetesDiscoveryClientReactiveAutoConfiguration.class))
					.withPropertyValues(properties);
		}

	}

}
