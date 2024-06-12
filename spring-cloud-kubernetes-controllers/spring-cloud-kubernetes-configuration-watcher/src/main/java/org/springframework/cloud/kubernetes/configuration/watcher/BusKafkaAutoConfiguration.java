/*
 * Copyright 2013-2024 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@Profile(KAFKA)
@Import(ContextFunctionCatalogAutoConfiguration.class)
@AutoConfigureAfter(RefreshTriggerAutoConfiguration.class)
class BusKafkaAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ConfigMapWatcherChangeDetector.class)
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	ConfigMapWatcherChangeDetector busConfigMapChangeWatcher(AbstractEnvironment environment, CoreV1Api coreV1Api,
			KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
			ConfigReloadProperties properties, KubernetesNamespaceProvider namespaceProvider,
			ConfigurationUpdateStrategy strategy,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, BusRefreshTrigger busRefreshTrigger) {
		return new BusEventBasedConfigMapWatcherChangeDetector(coreV1Api, environment, properties, strategy,
				configMapPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
				busRefreshTrigger);
	}

	@Bean
	@ConditionalOnMissingBean(SecretsWatcherChangeDetector.class)
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	SecretsWatcherChangeDetector busSecretsChangeWatcher(AbstractEnvironment environment, CoreV1Api coreV1Api,
			KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, KubernetesNamespaceProvider namespaceProvider,
			BusRefreshTrigger busRefreshTrigger) {
		return new BusEventBasedSecretsWatcherChangeDetector(coreV1Api, environment, properties, strategy,
				secretsPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
				busRefreshTrigger);
	}

}
