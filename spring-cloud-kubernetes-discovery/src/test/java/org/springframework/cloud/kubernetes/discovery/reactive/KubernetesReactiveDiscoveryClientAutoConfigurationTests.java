/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery.reactive;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tim Ysewyn
 */
class KubernetesReactiveDiscoveryClientAutoConfigurationTests {

	private ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(UtilAutoConfiguration.class,
					ReactiveCommonsClientAutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
					Fabric8AutoConfiguration.class, KubernetesDiscoveryClientAutoConfiguration.class,
					KubernetesReactiveDiscoveryClientAutoConfiguration.class));

	@Test
	public void shouldWorkWithDefaults() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ReactiveDiscoveryClient.class);
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenDiscoveryDisabled() {
		contextRunner.withPropertyValues("spring.cloud.discovery.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("kubernetesReactiveDiscoveryClient");
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenReactiveDiscoveryDisabled() {
		contextRunner.withPropertyValues("spring.cloud.discovery.reactive.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("kubernetesReactiveDiscoveryClient");
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenKubernetesDisabled() {
		contextRunner.withPropertyValues("spring.cloud.kubernetes.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("kubernetesReactiveDiscoveryClient");
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void shouldNotHaveDiscoveryClientWhenKubernetesDiscoveryDisabled() {
		contextRunner.withPropertyValues("spring.cloud.kubernetes.discovery.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("kubernetesReactiveDiscoveryClient");
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void worksWithoutWebflux() {
		contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.web.reactive")).run(context -> {
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	public void worksWithoutActuator() {
		contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate")).run(context -> {
			assertThat(context).hasSingleBean(ReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

}
