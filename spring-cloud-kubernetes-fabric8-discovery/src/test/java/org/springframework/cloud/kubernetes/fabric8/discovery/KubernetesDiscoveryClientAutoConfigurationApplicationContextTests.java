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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test various conditionals for {@link KubernetesDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientServicesFunction.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientServicesFunction.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientServicesFunction.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabledHealthIndicatorMissing() {
		setupWithFilteredClassLoader(HealthIndicator.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false", "spring.cloud.discovery.client.health-indicator.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
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
			assertThat(context).hasSingleBean(KubernetesClientServicesFunction.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClient.class);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner().withConfiguration(
				AutoConfigurations.of(KubernetesDiscoveryClientAutoConfiguration.class, Fabric8AutoConfiguration.class,
						KubernetesCommonsAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class))
				.withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(Class<?> cls, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesDiscoveryClientAutoConfiguration.class,
						Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
						KubernetesDiscoveryPropertiesAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader(cls)).withPropertyValues(properties);
	}

}
