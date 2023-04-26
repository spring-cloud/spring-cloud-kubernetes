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

import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@Deprecated(forRemoval = true)
public class ConfigurationWatcherAutoConfiguration {

	private static final String AMQP = "bus-amqp";

	private static final String KAFKA = "bus-kafka";

	public WebClient webClient(WebClient.Builder webClientBuilder) {
		return webClientBuilder.build();
	}

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

	static class BusRabbitConfiguration {

		public ConfigMapWatcherChangeDetector busConfigMapChangeWatcher(AbstractEnvironment environment,
				CoreV1Api coreV1Api, KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
				KubernetesNamespaceProvider kubernetesNamespaceProvider, ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory, BusRefreshTrigger busRefreshTrigger) {
			return new BusEventBasedConfigMapWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					configMapPropertySourceLocator, kubernetesNamespaceProvider, k8SConfigurationProperties,
					threadFactory, busRefreshTrigger);
		}

		public SecretsWatcherChangeDetector busSecretsChangeWatcher(AbstractEnvironment environment,
				CoreV1Api coreV1Api, KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
				ConfigReloadProperties properties, KubernetesNamespaceProvider kubernetesNamespaceProvider,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory, BusRefreshTrigger busRefreshTrigger) {
			return new BusEventBasedSecretsWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					secretsPropertySourceLocator, kubernetesNamespaceProvider, k8SConfigurationProperties,
					threadFactory, busRefreshTrigger);
		}

	}

	static class BusKafkaConfiguration {

		public ConfigMapWatcherChangeDetector busConfigMapChangeWatcher(AbstractEnvironment environment,
				CoreV1Api coreV1Api, KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
				ConfigReloadProperties properties, KubernetesNamespaceProvider namespaceProvider,
				ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory, BusRefreshTrigger busRefreshTrigger) {
			return new BusEventBasedConfigMapWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					configMapPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
					busRefreshTrigger);
		}

		public SecretsWatcherChangeDetector busSecretsChangeWatcher(AbstractEnvironment environment,
				CoreV1Api coreV1Api, KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator,
				ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
				ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
				ThreadPoolTaskExecutor threadFactory, KubernetesNamespaceProvider namespaceProvider,
				BusRefreshTrigger busRefreshTrigger) {
			return new BusEventBasedSecretsWatcherChangeDetector(coreV1Api, environment, properties, strategy,
					secretsPropertySourceLocator, namespaceProvider, k8SConfigurationProperties, threadFactory,
					busRefreshTrigger);
		}

	}

	static class RefreshTriggerConfiguration {

		public BusRefreshTrigger busRefreshTrigger(ApplicationEventPublisher applicationEventPublisher,
				BusProperties busProperties) {
			return new BusRefreshTrigger(applicationEventPublisher, busProperties.getId());
		}

		public HttpRefreshTrigger httpRefreshTrigger(KubernetesInformerReactiveDiscoveryClient client,
				ConfigurationWatcherConfigurationProperties properties, WebClient webClient) {
			return new HttpRefreshTrigger(client, properties, webClient);
		}

	}

}
