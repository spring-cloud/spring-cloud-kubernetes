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
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bus.BusStreamAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@EnableConfigurationProperties({ ConfigurationWatcherConfigurationProperties.class })
@AutoConfigureAfter({ RefreshTriggerAutoConfiguration.class, BusRabbitAutoConfiguration.class,
		BusKafkaAutoConfiguration.class })
@AutoConfigureBefore(BusStreamAutoConfiguration.class)
public class ConfigurationWatcherAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebClient webClient(WebClient.Builder webClientBuilder) {
		return webClientBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean(ConfigMapWatcherChangeDetector.class)
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	public ConfigMapWatcherChangeDetector httpBasedConfigMapWatchChangeDetector(AbstractEnvironment environment,
			CoreV1Api coreV1Api, KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			KubernetesNamespaceProvider namespaceProvider, ThreadPoolTaskExecutor threadFactory,
			HttpRefreshTrigger httpRefreshTrigger) {
		return new HttpBasedConfigMapWatchChangeDetector(coreV1Api, environment, properties, strategy,
				configMapPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
				httpRefreshTrigger);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	public SecretsWatcherChangeDetector httpBasedSecretsWatchChangeDetector(AbstractEnvironment environment,
			CoreV1Api coreV1Api, KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, HttpRefreshTrigger httpRefreshTrigger) {
		return new HttpBasedSecretsWatchChangeDetector(coreV1Api, environment, properties, strategy,
				secretsPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
				httpRefreshTrigger);
	}

}
