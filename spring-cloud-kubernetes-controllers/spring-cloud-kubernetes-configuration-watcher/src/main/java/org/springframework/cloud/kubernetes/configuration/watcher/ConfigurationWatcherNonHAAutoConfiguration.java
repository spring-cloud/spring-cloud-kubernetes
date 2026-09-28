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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rabbitmq.autoconfigure.health.RabbitHealthContributorAutoConfiguration;
import org.springframework.cloud.bus.BusStreamAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.configuration.watcher.ha.ConditionalOnConfigurationWatcherHANotEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

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
@EnableConfigurationProperties({ ConfigurationWatcherConfigurationProperties.class })
@AutoConfigureAfter(RefreshTriggerAutoConfiguration.class)
@AutoConfigureBefore(BusStreamAutoConfiguration.class)
class ConfigurationWatcherNonHAAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	WebClient webClient(WebClient.Builder webClientBuilder) {
		return webClientBuilder.build();
	}

	// only needed as a bean to the methods below
	// same one is used for HA and non-HA implementations
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	KubernetesClientEventBasedConfigMapChangeDetector configMapWatcherInformer(CoreV1Api coreV1Api,
			AbstractEnvironment environment, ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		return new KubernetesClientEventBasedConfigMapChangeDetector(strategy, propertySourceLocator, environment,
				coreV1Api, properties, namespaceProvider, KubernetesClientConfigMapPropertySource.class);
	}

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

	// only needed as a bean to the methods below
	// same one is used for HA and non-HA implementations
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	KubernetesClientEventBasedSecretsChangeDetector secretsWatcherInformer(CoreV1Api coreV1Api,
			AbstractEnvironment environment, ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		return new KubernetesClientEventBasedSecretsChangeDetector(strategy, propertySourceLocator, environment,
				coreV1Api, properties, namespaceProvider, KubernetesClientSecretsPropertySource.class);
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

	@Configuration(proxyBeanMethods = false)
	@Profile(KAFKA)
	@Import(ContextFunctionCatalogAutoConfiguration.class)
	static class KafkaConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@Profile(AMQP)
	@Import({ ContextFunctionCatalogAutoConfiguration.class, RabbitHealthContributorAutoConfiguration.class })
	static class RabbitConfiguration {

	}

}
