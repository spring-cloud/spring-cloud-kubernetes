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

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test various conditionals for {@link KubernetesClientCatalogWatch}
 *
 * @author wind57
 */
class KubernetesClientCatalogWatchAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesClientCatalogWatch.class));
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesClientCatalogWatch.class));
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.enabled=false");
		applicationContextRunner
			.run(context -> assertThat(context).doesNotHaveBean(KubernetesClientCatalogWatch.class));
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(KubernetesClientCatalogWatch.class));
	}

	// disabling discovery, disables catalog watcher.
	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner
			.run(context -> assertThat(context).doesNotHaveBean(KubernetesClientCatalogWatch.class));
	}

	/**
	 * both blocking and reactive configs are disabled, catalog watcher is disabled also.
	 */
	@Test
	void disableBlockingAndReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientCatalogWatch.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(SharedInformerFactory.class);
			assertThat(context).doesNotHaveBean(SharedIndexInformer.class);
			assertThat(context).doesNotHaveBean(Lister.class);
		});
	}

	/**
	 * blocking is disabled, reactive is enabled, catalog watcher is enabled.
	 */
	@Test
	void disableBlockingEnableReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.blocking.enabled=false",
				"spring.cloud.discovery.reactive.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientCatalogWatch.class);
		});
	}

	/**
	 * blocking is enabled, reactive is disabled, catalog watcher is enabled.
	 */
	@Test
	void enableBlockingDisableReactive() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.discovery.blocking.enabled=true",
				"spring.cloud.discovery.reactive.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientCatalogWatch.class);
		});
	}

	/**
	 * spring.cloud.kubernetes.discovery.enabled is false, catalog watcher is disabled
	 * also.
	 */
	@Test
	void disableKubernetesDiscovery() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesClientCatalogWatch.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(KubernetesClientCatalogWatchAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class))
			.withPropertyValues(properties);
	}

}
