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

package org.springframework.cloud.kubernetes.client.config.reload;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.commons.util.TaskSchedulerWrapper;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.ConditionalOnConfigMapsReloadEnabled;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.ConditionalOnKubernetesReloadEnabled;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.ConditionalOnSecretsReloadEnabled;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.EventReloadDetectionMode;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.PollingReloadDetectionMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.TaskScheduler;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnKubernetesReloadEnabled
@ConditionalOnClass({ EndpointAutoConfiguration.class, RestartEndpoint.class, ContextRefresher.class })
@AutoConfigureAfter({ InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
		RefreshAutoConfiguration.class, ConfigReloadPropertiesAutoConfiguration.class })
@Import(ConfigReloadAutoConfiguration.class)
public class KubernetesClientConfigReloadAutoConfiguration {

	/**
	 * Polling configMap ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param configMapPropertySourceLocator configMap property source locator
	 * @return a bean that listen to configuration changes and fire a reload.
	 */
	@Bean
	@ConditionalOnConfigMapsReloadEnabled
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Conditional(PollingReloadDetectionMode.class)
	public ConfigurationChangeDetector configMapPropertyChangePollingWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
			AbstractEnvironment environment, TaskSchedulerWrapper<TaskScheduler> taskScheduler) {

		return new PollingConfigMapChangeDetector(environment, properties, strategy,
				KubernetesClientConfigMapPropertySource.class, configMapPropertySourceLocator,
				taskScheduler.getTaskScheduler());
	}

	/**
	 * Polling secrets ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param secretsPropertySourceLocator secrets property source locator
	 * @return a bean that listen to configuration changes and fire a reload.
	 */
	@Bean
	@ConditionalOnSecretsReloadEnabled
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Conditional(PollingReloadDetectionMode.class)
	public ConfigurationChangeDetector secretsPropertyChangePollingWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator, AbstractEnvironment environment,
			TaskSchedulerWrapper<TaskScheduler> taskScheduler) {

		return new PollingSecretsChangeDetector(environment, properties, strategy,
				KubernetesClientSecretsPropertySource.class, secretsPropertySourceLocator,
				taskScheduler.getTaskScheduler());
	}

	/**
	 * Event Based configMap ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param configMapPropertySourceLocator configMap property source locator
	 * @return a bean that listen to configMap change events and fire a reload.
	 */
	@Bean
	@ConditionalOnConfigMapsReloadEnabled
	@ConditionalOnBean(KubernetesClientConfigMapPropertySourceLocator.class)
	@Conditional(EventReloadDetectionMode.class)
	public ConfigurationChangeDetector configMapPropertyChangeEventWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator,
			AbstractEnvironment environment, CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {

		return new KubernetesClientEventBasedConfigMapChangeDetector(coreV1Api, environment, properties, strategy,
				configMapPropertySourceLocator, kubernetesNamespaceProvider);
	}

	/**
	 * Event Based secrets ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param secretsPropertySourceLocator secrets property source locator
	 * @return a bean that listen to secrets change events and fire a reload.
	 */
	@Bean
	@ConditionalOnSecretsReloadEnabled
	@ConditionalOnBean(KubernetesClientSecretsPropertySourceLocator.class)
	@Conditional(EventReloadDetectionMode.class)
	public ConfigurationChangeDetector secretsPropertyChangeEventWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator, AbstractEnvironment environment,
			CoreV1Api coreV1Api, KubernetesNamespaceProvider kubernetesNamespaceProvider) {

		return new KubernetesClientEventBasedSecretsChangeDetector(coreV1Api, environment, properties, strategy,
				secretsPropertySourceLocator, kubernetesNamespaceProvider);
	}

}
