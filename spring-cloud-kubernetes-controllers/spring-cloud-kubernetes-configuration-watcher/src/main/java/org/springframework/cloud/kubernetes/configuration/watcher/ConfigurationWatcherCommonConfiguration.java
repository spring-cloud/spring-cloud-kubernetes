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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rabbitmq.autoconfigure.health.RabbitHealthContributorAutoConfiguration;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * Configures infrastructure shared by the HA and non-HA configuration watcher modes.
 *
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ ConfigurationWatcherConfigurationProperties.class })
public class ConfigurationWatcherCommonConfiguration {

	@Bean
	@ConditionalOnMissingBean
	WebClient webClient(WebClient.Builder webClientBuilder) {
		return webClientBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	KubernetesClientEventBasedConfigMapChangeDetector configMapChangeDetector(CoreV1Api coreV1Api,
			AbstractEnvironment environment, ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		return new KubernetesClientEventBasedConfigMapChangeDetector(strategy, propertySourceLocator, environment,
				coreV1Api, properties, namespaceProvider, KubernetesClientConfigMapPropertySource.class);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	KubernetesClientEventBasedSecretsChangeDetector secretsChangeDetector(CoreV1Api coreV1Api,
			AbstractEnvironment environment, ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		return new KubernetesClientEventBasedSecretsChangeDetector(strategy, propertySourceLocator, environment,
				coreV1Api, properties, namespaceProvider, KubernetesClientSecretsPropertySource.class);
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
