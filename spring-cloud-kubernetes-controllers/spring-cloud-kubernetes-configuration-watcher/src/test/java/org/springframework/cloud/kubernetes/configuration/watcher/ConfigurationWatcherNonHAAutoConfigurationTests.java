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

package org.springframework.cloud.kubernetes.configuration.watcher;

import io.kubernetes.client.openapi.apis.CoreV1Api;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * Tests the watcher beans created when HA is disabled.
 *
 * @author wind57
 */
class ConfigurationWatcherNonHAAutoConfigurationTests {

	@Test
	void createsAndStartsHttpWatchersWithoutHaOrBusProfile() {
		applicationContextRunner().run(context -> {
			assertThat(context).hasSingleBean(HttpBasedConfigMapWatchChangeDetector.class);
			assertThat(context).hasSingleBean(HttpBasedSecretsWatchChangeDetector.class);
			assertThat(context).doesNotHaveBean(BusEventBasedConfigMapWatcherChangeDetector.class);
			assertThat(context).doesNotHaveBean(BusEventBasedSecretsWatcherChangeDetector.class);

			verify(context.getBean(KubernetesClientEventBasedConfigMapChangeDetector.class)).start(any());
			verify(context.getBean(KubernetesClientEventBasedSecretsChangeDetector.class)).start(any());
		});
	}

	@Test
	void createsAndStartsBusWatchersWithAmqpProfile() {
		applicationContextRunner().withPropertyValues("spring.profiles.active=" + AMQP).run(context -> {
			assertThat(context).hasSingleBean(BusEventBasedConfigMapWatcherChangeDetector.class);
			assertThat(context).hasSingleBean(BusEventBasedSecretsWatcherChangeDetector.class);
			assertThat(context).doesNotHaveBean(HttpBasedConfigMapWatchChangeDetector.class);
			assertThat(context).doesNotHaveBean(HttpBasedSecretsWatchChangeDetector.class);
			assertThat(context).hasSingleBean(FunctionCatalog.class);

			verify(context.getBean(KubernetesClientEventBasedConfigMapChangeDetector.class)).start(any());
			verify(context.getBean(KubernetesClientEventBasedSecretsChangeDetector.class)).start(any());
		});
	}

	@Test
	void createsAndStartsBusWatchersWithKafkaProfile() {
		applicationContextRunner().withPropertyValues("spring.profiles.active=" + KAFKA).run(context -> {
			assertThat(context).hasSingleBean(BusEventBasedConfigMapWatcherChangeDetector.class);
			assertThat(context).hasSingleBean(BusEventBasedSecretsWatcherChangeDetector.class);
			assertThat(context).doesNotHaveBean(HttpBasedConfigMapWatchChangeDetector.class);
			assertThat(context).doesNotHaveBean(HttpBasedSecretsWatchChangeDetector.class);
			assertThat(context).hasSingleBean(FunctionCatalog.class);

			verify(context.getBean(KubernetesClientEventBasedConfigMapChangeDetector.class)).start(any());
			verify(context.getBean(KubernetesClientEventBasedSecretsChangeDetector.class)).start(any());
		});
	}

	@Test
	void doesNotCreateWatchersWhenHaIsEnabled() {
		applicationContextRunner().withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(ConfigMapWatcherChangeDetector.class);
				assertThat(context).doesNotHaveBean(SecretsWatcherChangeDetector.class);
			});
	}

	private ApplicationContextRunner applicationContextRunner() {
		return new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class, ConfigurationWatcherNonHAAutoConfiguration.class)
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES");
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		WebClient.Builder webClientBuilder() {
			return WebClient.builder();
		}

		@Bean
		CoreV1Api coreV1Api() {
			return mock(CoreV1Api.class);
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
