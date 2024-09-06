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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8KubernetesCatalogWatchAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(Fabric8KubernetesCatalogWatch.class));
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(Fabric8KubernetesCatalogWatch.class));
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner
			.run(context -> assertThat(context).doesNotHaveBean(Fabric8KubernetesCatalogWatch.class));
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(Fabric8KubernetesCatalogWatch.class));
	}

	// disabling discovery should disable catalog watcher.
	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner
			.run(context -> assertThat(context).doesNotHaveBean(Fabric8KubernetesCatalogWatch.class));
	}

	/**
	 * both blocking and reactive configs are disabled, catalog watcher is disabled too.
	 */
	@Test
	void disableBlockingAndReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesReactiveDiscoveryClient.class);
		});
	}

	/**
	 * blocking is disabled, reactive is enabled, catalog watcher is enabled.
	 */
	@Test
	void disableBlockingEnableReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.discovery.reactive.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesReactiveDiscoveryClient.class);
		});
	}

	/**
	 * blocking is enabled, reactive is disabled, catalog watcher is enabled.
	 */
	@Test
	void enableBlockingDisableReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true", "spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesReactiveDiscoveryClient.class);
		});
	}

	/**
	 * spring.cloud.kubernetes.discovery.enabled is false, catalog watcher is disabled
	 * also.
	 */
	@Test
	void disableKubernetesDiscovery() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(Fabric8KubernetesReactiveDiscoveryClient.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(Fabric8KubernetesCatalogWatchAutoConfiguration.class,
					Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
					KubernetesDiscoveryPropertiesAutoConfiguration.class,
					Fabric8DiscoveryClientPredicateAutoConfiguration.class))
			.withPropertyValues(properties);
	}

}
