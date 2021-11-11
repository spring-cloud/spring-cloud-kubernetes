/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
class KubernetesDiscoveryClientAutoConfigurationTests {

	private ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(UtilAutoConfiguration.class,
					ReactiveCommonsClientAutoConfiguration.class, KubernetesDiscoveryClientAutoConfiguration.class));

	@Test
	public void shouldWorkWithDefaults() {
		contextRunner
				.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver")
				.withClassLoader(new FilteredClassLoader("org.springframework.web.reactive")).run(context -> {
					assertThat(context).hasSingleBean(DiscoveryClient.class);
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
				});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenDiscoveryDisabled() {
		contextRunner
				.withPropertyValues("spring.cloud.discovery.enabled=false",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver")
				.run(context -> {
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
					assertThat(context).doesNotHaveBean(DiscoveryClient.class);
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
				});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenKubernetesDiscoveryDisabled() {
		contextRunner
				.withPropertyValues("spring.cloud.kubernetes.discovery.enabled=false",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver")
				.run(context -> {
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
					assertThat(context).doesNotHaveBean(DiscoveryClient.class);
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
				});
	}

	@Test
	public void shouldHaveReactiveDiscoveryClient() {
		contextRunner
				.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver")
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveDiscoveryClient.class);
					assertThat(context).doesNotHaveBean(DiscoveryClient.class);
					assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
				});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenReactiveDiscoveryDisabled() {
		contextRunner.withPropertyValues("spring.cloud.discovery.reactive.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenKubernetesDisabled() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void worksWithoutActuator() {
		contextRunner
				.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://k8sdiscoveryserver")
				.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate")).run(context -> {
					assertThat(context).hasSingleBean(ReactiveDiscoveryClient.class);
					assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
				});
	}

}
