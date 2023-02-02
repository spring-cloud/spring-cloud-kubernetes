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

package org.springframework.cloud.kubernetes.client.discovery;

import java.io.StringReader;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test various conditionals for
 * {@link KubernetesInformerDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesInformerDiscoveryClientAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	private static K3sContainer container;

	@AfterAll
	static void afterAll() {
		container.stop();
	}

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(CatalogSharedInformerFactory.class);
			assertThat(context).doesNotHaveBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(CatalogSharedInformerFactory.class);
			assertThat(context).doesNotHaveBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).doesNotHaveBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).doesNotHaveBean(CatalogSharedInformerFactory.class);
			assertThat(context).doesNotHaveBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	@Test
	void kubernetesDiscoveryHealthIndicatorDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.client.health-indicator.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
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
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasSingleBean(KubernetesInformerDiscoveryClient.class);
			assertThat(context).hasSingleBean(CatalogSharedInformerFactory.class);
			assertThat(context).hasSingleBean(SpringCloudKubernetesInformerFactoryProcessor.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesInformerDiscoveryClientAutoConfiguration.class,
						KubernetesClientAutoConfiguration.class))
				.withUserConfiguration(ApiClientConfig.class).withPropertyValues(properties);
	}

	@Configuration
	static class ApiClientConfig {

		@Bean
		@Primary
		ApiClient apiClient() throws Exception {
			container = Commons.container();
			container.start();

			return Config.fromConfig(new StringReader(container.getKubeConfigYaml()));
		}

	}

}
