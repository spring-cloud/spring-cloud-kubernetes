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
import org.jspecify.annotations.NonNull;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionCallbacksAutoConfiguration;
import org.springframework.cloud.kubernetes.configuration.watcher.BusEventBasedConfigMapWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.BusEventBasedSecretsWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.BusRefreshTrigger;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherCommonConfiguration;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedConfigMapWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedSecretsWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpRefreshTrigger;
import org.springframework.cloud.kubernetes.configuration.watcher.SecretsWatcherChangeDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.NOT_AMQP_NOT_KAFKA;

/**
 * Configures the components required for configuration watcher HA.
 *
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnConfigurationWatcherHAEnabled
@ConditionalOnBean(ApiClient.class)
@Import(ConfigurationWatcherCommonConfiguration.class)
@AutoConfigureAfter(KubernetesClientAutoConfiguration.class)
@AutoConfigureBefore(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class)
class ConfigurationWatcherHAAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ConfigurationWatcherHACoordinator configurationWatcherHACoordinator(
			ObjectProvider<@NonNull ConfigMapWatcherChangeDetector> configMapDetector,
			ObjectProvider<@NonNull SecretsWatcherChangeDetector> secretsDetector) {
		return new ConfigurationWatcherHACoordinator(configMapDetector, secretsDetector);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Profile(NOT_AMQP_NOT_KAFKA)
	ConfigMapWatcherChangeDetector httpBasedConfigMapWatchChangeDetector(
			KubernetesClientEventBasedConfigMapChangeDetector configMapChangeDetector,
			ConfigurationWatcherConfigurationProperties properties, ThreadPoolTaskExecutor threadFactory,
			HttpRefreshTrigger httpRefreshTrigger) {
		return new HttpBasedConfigMapWatchChangeDetector(httpRefreshTrigger, configMapChangeDetector, properties,
				threadFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Profile({ AMQP, KAFKA })
	ConfigMapWatcherChangeDetector busConfigMapWatchChangeDetector(
			KubernetesClientEventBasedConfigMapChangeDetector configMapChangeDetector,
			ConfigurationWatcherConfigurationProperties properties, ThreadPoolTaskExecutor threadFactory,
			BusRefreshTrigger busRefreshTrigger) {
		return new BusEventBasedConfigMapWatcherChangeDetector(busRefreshTrigger, configMapChangeDetector, properties,
				threadFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Profile({ AMQP, KAFKA })
	SecretsWatcherChangeDetector busSecretsWatchChangeDetector(
			KubernetesClientEventBasedSecretsChangeDetector secretsChangeDetector,
			ConfigurationWatcherConfigurationProperties properties, ThreadPoolTaskExecutor threadFactory,
			BusRefreshTrigger busRefreshTrigger) {
		return new BusEventBasedSecretsWatcherChangeDetector(secretsChangeDetector, properties, threadFactory,
				busRefreshTrigger);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Profile(NOT_AMQP_NOT_KAFKA)
	SecretsWatcherChangeDetector httpBasedSecretsWatchChangeDetector(
			KubernetesClientEventBasedSecretsChangeDetector secretsChangeDetector,
			ConfigurationWatcherConfigurationProperties properties, ThreadPoolTaskExecutor threadFactory,
			HttpRefreshTrigger httpRefreshTrigger) {
		return new HttpBasedSecretsWatchChangeDetector(httpRefreshTrigger, secretsChangeDetector, properties,
				threadFactory);
	}

}
