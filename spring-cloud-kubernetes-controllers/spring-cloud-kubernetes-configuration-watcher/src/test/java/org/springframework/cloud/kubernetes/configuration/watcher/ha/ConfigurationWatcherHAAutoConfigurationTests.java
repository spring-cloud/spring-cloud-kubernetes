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

package org.springframework.cloud.kubernetes.configuration.watcher.ha;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author wind57
 */
class ConfigurationWatcherHAAutoConfigurationTests {

	@Test
	void createsCoordinatorWhenHaAndLeaderElectionAreEnabled() {
		applicationContextRunner()
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(ConfigurationWatcherHACoordinator.class));
	}

	@Test
	void doesNotCreateCoordinatorWhenLeaderElectionIsDisabled() {
		applicationContextRunner()
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ConfigurationWatcherHACoordinator.class));
	}

	@Test
	void doesNotCreateCoordinatorWhenHaIsDisabled() {
		applicationContextRunner()
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=false",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean(ConfigurationWatcherHACoordinator.class));
	}

	private ApplicationContextRunner applicationContextRunner() {
		return new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class, ConfigurationWatcherHAAutoConfiguration.class)
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES");
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		ApiClient apiClient() {
			return mock(ApiClient.class);
		}

		@Bean
		KubernetesClientEventBasedConfigMapChangeDetector configMapDetector() {
			return mock(KubernetesClientEventBasedConfigMapChangeDetector.class);
		}

		@Bean
		KubernetesClientEventBasedSecretsChangeDetector secretsDetector() {
			return mock(KubernetesClientEventBasedSecretsChangeDetector.class);
		}

		@Bean
		ConfigurationWatcherConfigurationProperties properties() {
			return new ConfigurationWatcherConfigurationProperties();
		}

	}

}
