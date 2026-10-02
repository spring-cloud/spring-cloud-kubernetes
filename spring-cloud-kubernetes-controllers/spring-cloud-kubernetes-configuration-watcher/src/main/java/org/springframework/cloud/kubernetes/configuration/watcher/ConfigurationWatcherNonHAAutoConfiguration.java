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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.bus.BusStreamAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.ha.ConditionalOnConfigurationWatcherHANotEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.NOT_AMQP_NOT_KAFKA;

/**
 * This is the non-HA auto configuration. It calls "detector::start" as soon as the bean
 * is created.
 *
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnConfigurationWatcherHANotEnabled
@Import(ConfigurationWatcherCommonConfiguration.class)
@AutoConfigureAfter(RefreshTriggerAutoConfiguration.class)
@AutoConfigureBefore(BusStreamAutoConfiguration.class)
class ConfigurationWatcherNonHAAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Profile({ AMQP, KAFKA })
	ConfigMapWatcherChangeDetector busConfigMapWatchChangeDetector(
			ConfigurationWatcherConfigurationProperties properties, ThreadPoolTaskExecutor threadFactory,
			BusRefreshTrigger busRefreshTrigger, KubernetesClientEventBasedConfigMapChangeDetector changeDetector) {
		ConfigMapWatcherChangeDetector detector = new BusEventBasedConfigMapWatcherChangeDetector(busRefreshTrigger,
				changeDetector, properties, threadFactory);

		detector.start();

		return detector;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Profile(NOT_AMQP_NOT_KAFKA)
	ConfigMapWatcherChangeDetector httpBasedConfigMapWatchChangeDetector(
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, HttpRefreshTrigger httpRefreshTrigger,
			KubernetesClientEventBasedConfigMapChangeDetector changeDetector) {
		ConfigMapWatcherChangeDetector detector = new HttpBasedConfigMapWatchChangeDetector(httpRefreshTrigger,
				changeDetector, k8SConfigurationProperties, threadFactory);

		detector.start();

		return detector;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Profile({ AMQP, KAFKA })
	SecretsWatcherChangeDetector busSecretsWatchChangeDetector(ConfigurationWatcherConfigurationProperties properties,
			ThreadPoolTaskExecutor threadFactory, BusRefreshTrigger busRefreshTrigger,
			KubernetesClientEventBasedSecretsChangeDetector changeDetector) {
		SecretsWatcherChangeDetector detector = new BusEventBasedSecretsWatcherChangeDetector(changeDetector,
				properties, threadFactory, busRefreshTrigger);

		detector.start();

		return detector;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Profile(NOT_AMQP_NOT_KAFKA)
	SecretsWatcherChangeDetector httpBasedSecretsWatchChangeDetector(
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, HttpRefreshTrigger httpRefreshTrigger,
			KubernetesClientEventBasedSecretsChangeDetector changeDetector) {
		SecretsWatcherChangeDetector detector = new HttpBasedSecretsWatchChangeDetector(httpRefreshTrigger,
				changeDetector, k8SConfigurationProperties, threadFactory);

		detector.start();

		return detector;
	}

}
