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
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.cloud.kubernetes.configuration.watcher.BusEventBasedConfigMapWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.BusEventBasedSecretsWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.BusRefreshTrigger;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedConfigMapWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedSecretsWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpRefreshTrigger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * @author wind57
 */
class ConfigurationWatcherHAAutoConfigurationTests {

	@Test
	void createsCoordinatorAndHttpWatchersWhenHaIsEnabled() {
		applicationContextRunner().withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(ConfigurationWatcherHACoordinator.class);
				assertThat(context).hasSingleBean(HttpBasedConfigMapWatchChangeDetector.class);
				assertThat(context).hasSingleBean(HttpBasedSecretsWatchChangeDetector.class);
				assertThat(context).doesNotHaveBean(BusEventBasedConfigMapWatcherChangeDetector.class);
				assertThat(context).doesNotHaveBean(BusEventBasedSecretsWatcherChangeDetector.class);

				KubernetesClientEventBasedConfigMapChangeDetector configMapDetector = context
					.getBean(KubernetesClientEventBasedConfigMapChangeDetector.class);
				KubernetesClientEventBasedSecretsChangeDetector secretsDetector = context
					.getBean(KubernetesClientEventBasedSecretsChangeDetector.class);
				verify(configMapDetector, never()).start(any());
				verify(secretsDetector, never()).start(any());

				ConfigurationWatcherHACoordinator coordinator = context
					.getBean(ConfigurationWatcherHACoordinator.class);
				coordinator.onApplicationEvent(new StartLeadingEvent("candidate"));
				verify(configMapDetector).start(any());
				verify(secretsDetector).start(any());

				coordinator.onApplicationEvent(new StopLeadingEvent("candidate"));
				verify(configMapDetector).stop();
				verify(secretsDetector).stop();
			});
	}

	@Test
	void createsCoordinatorAndBusWatchersWithAmqpProfile() {
		applicationContextRunner()
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.profiles.active=" + AMQP)
			.run(context -> assertBusWatchers(context));
	}

	@Test
	void createsCoordinatorAndBusWatchersWithKafkaProfile() {
		applicationContextRunner()
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.profiles.active=" + KAFKA)
			.run(context -> assertBusWatchers(context));
	}

	@Test
	void doesNotCreateCoordinatorWhenHaIsDisabledEvenWhenLeaderElectionIsEnabled() {
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

	private void assertBusWatchers(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
		assertThat(context).hasSingleBean(ConfigurationWatcherHACoordinator.class);
		assertThat(context).hasSingleBean(BusEventBasedConfigMapWatcherChangeDetector.class);
		assertThat(context).hasSingleBean(BusEventBasedSecretsWatcherChangeDetector.class);
		assertThat(context).doesNotHaveBean(HttpBasedConfigMapWatchChangeDetector.class);
		assertThat(context).doesNotHaveBean(HttpBasedSecretsWatchChangeDetector.class);
		assertThat(context).hasSingleBean(FunctionCatalog.class);

		KubernetesClientEventBasedConfigMapChangeDetector configMapDetector = context
			.getBean(KubernetesClientEventBasedConfigMapChangeDetector.class);
		KubernetesClientEventBasedSecretsChangeDetector secretsDetector = context
			.getBean(KubernetesClientEventBasedSecretsChangeDetector.class);
		verify(configMapDetector, never()).start(any());
		verify(secretsDetector, never()).start(any());
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		WebClient.Builder webClientBuilder() {
			return WebClient.builder();
		}

		@Bean
		ApiClient apiClient() {
			return mock(ApiClient.class);
		}

		@Bean
		KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator() {
			return mock(KubernetesClientConfigMapPropertySourceLocator.class);
		}

		@Bean
		KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator() {
			return mock(KubernetesClientSecretsPropertySourceLocator.class);
		}

		@Bean
		ConfigReloadProperties configReloadProperties() {
			return ConfigReloadProperties.DEFAULT;
		}

		@Bean
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return ConfigurationUpdateStrategy.NOOP;
		}

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			return new KubernetesNamespaceProvider("default");
		}

		@Bean
		KubernetesClientEventBasedConfigMapChangeDetector configMapChangeDetector() {
			return mock(KubernetesClientEventBasedConfigMapChangeDetector.class);
		}

		@Bean
		KubernetesClientEventBasedSecretsChangeDetector secretsChangeDetector() {
			return mock(KubernetesClientEventBasedSecretsChangeDetector.class);
		}

		@Bean
		HttpRefreshTrigger httpRefreshTrigger() {
			return mock(HttpRefreshTrigger.class);
		}

		@Bean
		BusRefreshTrigger busRefreshTrigger() {
			return mock(BusRefreshTrigger.class);
		}

		@Bean
		ThreadPoolTaskExecutor threadPoolTaskExecutor() {
			return mock(ThreadPoolTaskExecutor.class);
		}

	}

}
