/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import io.fabric8.kubernetes.client.KubernetesClient;

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
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.TaskScheduler;

/**
 * Definition of beans needed for the automatic reload of configuration.
 *
 * @author Nicolla Ferraro
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnKubernetesReloadEnabled
@ConditionalOnClass({ EndpointAutoConfiguration.class, RestartEndpoint.class, ContextRefresher.class })
@AutoConfigureAfter({ InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
		RefreshAutoConfiguration.class, ConfigReloadPropertiesAutoConfiguration.class })
@Import(ConfigReloadAutoConfiguration.class)
public class Fabric8ConfigReloadAutoConfiguration {

	/**
	 * Polling configMap ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param fabric8ConfigMapPropertySourceLocator configMap property source locator
	 * @return a bean that listen to configuration changes and fire a reload.
	 */
	@Bean
	@ConditionalOnConfigMapsReloadEnabled
	@ConditionalOnBean(Fabric8ConfigMapPropertySourceLocator.class)
	@Conditional(PollingReloadDetectionMode.class)
	public ConfigurationChangeDetector configMapPropertyChangePollingWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
			TaskSchedulerWrapper<TaskScheduler> taskSchedulerWrapper, AbstractEnvironment environment) {

		return new PollingConfigMapChangeDetector(environment, properties, strategy,
				Fabric8ConfigMapPropertySource.class, fabric8ConfigMapPropertySourceLocator,
				taskSchedulerWrapper.getTaskScheduler());
	}

	/**
	 * Polling secrets ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param fabric8SecretsPropertySourceLocator secrets property source locator
	 * @return a bean that listen to configuration changes and fire a reload.
	 */
	@Bean
	@ConditionalOnSecretsReloadEnabled
	@ConditionalOnBean(Fabric8SecretsPropertySourceLocator.class)
	@Conditional(PollingReloadDetectionMode.class)
	public ConfigurationChangeDetector secretsPropertyChangePollingWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
			TaskSchedulerWrapper<TaskScheduler> taskScheduler, AbstractEnvironment environment) {

		return new PollingSecretsChangeDetector(environment, properties, strategy, Fabric8SecretsPropertySource.class,
				fabric8SecretsPropertySourceLocator, taskScheduler.getTaskScheduler());
	}

	/**
	 * Event Based configMap ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param fabric8ConfigMapPropertySourceLocator configMap property source locator
	 * @return a bean that listen to configMap change events and fire a reload.
	 */
	@Bean
	@ConditionalOnConfigMapsReloadEnabled
	@ConditionalOnBean(Fabric8ConfigMapPropertySourceLocator.class)
	@Conditional(EventReloadDetectionMode.class)
	public ConfigurationChangeDetector configMapPropertyChangeEventWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
			AbstractEnvironment environment, KubernetesClient kubernetesClient) {

		return new Fabric8EventBasedConfigMapChangeDetector(environment, properties, kubernetesClient, strategy,
				fabric8ConfigMapPropertySourceLocator, new KubernetesNamespaceProvider(environment));
	}

	/**
	 * Event Based secrets ConfigurationChangeDetector.
	 * @param properties config reload properties
	 * @param strategy configuration update strategy
	 * @param fabric8SecretsPropertySourceLocator secrets property source locator
	 * @return a bean that listen to secrets change events and fire a reload.
	 */
	@Bean
	@ConditionalOnSecretsReloadEnabled
	@ConditionalOnBean(Fabric8SecretsPropertySourceLocator.class)
	@Conditional(EventReloadDetectionMode.class)
	public ConfigurationChangeDetector secretsPropertyChangeEventWatcher(ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator, AbstractEnvironment environment,
			KubernetesClient kubernetesClient) {

		return new Fabric8EventBasedSecretsChangeDetector(environment, properties, kubernetesClient, strategy,
				fabric8SecretsPropertySourceLocator, new KubernetesNamespaceProvider(environment));
	}

}
