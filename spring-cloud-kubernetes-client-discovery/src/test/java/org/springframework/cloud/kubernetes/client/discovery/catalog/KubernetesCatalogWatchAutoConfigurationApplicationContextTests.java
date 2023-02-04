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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test various conditionals for {@link KubernetesCatalogWatch}
 *
 * @author wind57
 */
class KubernetesCatalogWatchAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesCatalogWatch.class));
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesCatalogWatch.class));
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).doesNotHaveBean(KubernetesCatalogWatch.class));
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesCatalogWatch.class));
	}

	// disabling discovery has no impact on the catalog watch.
	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesCatalogWatch.class));
	}

	/**
	 * both blocking and reactive configs are disabled, should not influence catalog
	 * watcher in any way.
	 */
	@Test
	void disableBlockingAndReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
		});
	}

	/**
	 * both blocking and reactive configs are disabled, should not influence catalog
	 * watcher in any way.
	 */
	@Test
	void disableKubernetesDiscovery() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesCatalogWatch.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesCatalogWatchAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class))
				.withPropertyValues(properties);
	}

}
