/*
 * Copyright 2013-2020 the original author or authors.
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

import org.springframework.boot.actuate.autoconfigure.amqp.RabbitHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ ConfigurationWatcherConfigurationProperties.class })
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
			KubernetesNamespaceProvider namespaceProvider, ThreadPoolTaskExecutor threadFactory, WebClient webClient,
			KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient) {
		return new HttpBasedConfigMapWatchChangeDetector(coreV1Api, environment, properties, strategy,
				configMapPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory, webClient,
				kubernetesReactiveDiscoveryClient);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	public SecretsWatcherChangeDetector httpBasedSecretsWatchChangeDetector(AbstractEnvironment environment,
			CoreV1Api coreV1Api, KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadFactory, WebClient webClient,
			KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient) {
		return new HttpBasedSecretsWatchChangeDetector(coreV1Api, environment, properties, strategy,
				secretsPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory, webClient,
				kubernetesReactiveDiscoveryClient);
	}

	@Configuration
	@Profile("bus-amqp")
	@Import({ ContextFunctionCatalogAutoConfiguration.class, RabbitHealthContributorAutoConfiguration.class })
	static class BusRabbitConfiguration {

		@Bean
		@ConditionalOnMissingBean(ConfigMapWatcherChangeDetector.class)
		@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
		public ConfigMapWatcherChangeDetector busConfigMapChangeWatcher(BusProperties busProperties,
				AbstractEnvironment environment, CoreV1Api coreV1Api,
				KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
				KubernetesNamespaceProvider kubernetesNamespaceProvider, ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory) {
			return new BusEventBasedConfigMapWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					configMapPropertySourceLocator, kubernetesNamespaceProvider, busProperties,
					k8SConfigurationProperties, threadFactory);
		}

		@Bean
		@ConditionalOnMissingBean(SecretsWatcherChangeDetector.class)
		@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
		public SecretsWatcherChangeDetector busSecretsChangeWatcher(BusProperties busProperties,
				AbstractEnvironment environment, CoreV1Api coreV1Api,
				KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
				ConfigReloadProperties properties, KubernetesNamespaceProvider kubernetesNamespaceProvider,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory) {
			return new BusEventBasedSecretsWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					secretsPropertySourceLocator, kubernetesNamespaceProvider, busProperties,
					k8SConfigurationProperties, threadFactory);
		}

	}

	@Configuration
	@Profile("bus-kafka")
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	static class BusKafkaConfiguration {

		@Bean
		@ConditionalOnMissingBean(ConfigMapWatcherChangeDetector.class)
		@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
		public ConfigMapWatcherChangeDetector busConfigMapChangeWatcher(BusProperties busProperties,
				AbstractEnvironment environment, CoreV1Api coreV1Api,
				KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
				ConfigReloadProperties properties, KubernetesNamespaceProvider namespaceProvider,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory) {
			return new BusEventBasedConfigMapWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					configMapPropertySourceLocator, namespaceProvider, busProperties, k8SConfigurationProperties,
					threadFactory);
		}

		@Bean
		@ConditionalOnMissingBean(SecretsWatcherChangeDetector.class)
		@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
		public SecretsWatcherChangeDetector busSecretsChangeWatcher(BusProperties busProperties,
				AbstractEnvironment environment, CoreV1Api coreV1Api,
				KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
				ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory, KubernetesNamespaceProvider namespaceProvider) {
			return new BusEventBasedSecretsWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					secretsPropertySourceLocator, namespaceProvider, busProperties, k8SConfigurationProperties,
					threadFactory);
		}

	}

}
