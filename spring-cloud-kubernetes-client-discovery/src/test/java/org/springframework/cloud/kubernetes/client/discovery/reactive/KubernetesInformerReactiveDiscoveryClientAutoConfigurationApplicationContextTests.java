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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test various conditionals for
 * {@link KubernetesInformerReactiveDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesInformerReactiveDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
		});
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
		});
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.discovery.reactive.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
		});
	}

	@Test
	void kubernetesReactiveDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
		});
	}

	/**
	 * blocking is disabled, and it should not impact reactive in any way.
	 */
	@Test
	void testBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
			"spring.cloud.discovery.blocking.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).size().isEqualTo(2);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
				KubernetesInformerReactiveDiscoveryClientAutoConfiguration.class,
			KubernetesClientAutoConfiguration.class, KubernetesInformerDiscoveryAutoConfiguration.class,
				SimpleReactiveDiscoveryClientAutoConfiguration.class, UtilAutoConfiguration.class))
			.withPropertyValues(properties);
	}

}
