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

package org.springframework.cloud.kubernetes.config.reload;

import java.util.concurrent.ThreadLocalRandom;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.reload.condition.EventReloadDetectionMode;
import org.springframework.cloud.kubernetes.config.reload.condition.PollingReloadDetectionMode;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@ConditionalOnMissingBean(ConfigReloadAutoConfiguration.class)
@EnableConfigurationProperties(ConfigReloadProperties.class)
public class ConfigReloadDefaultAutoConfiguration {

	/**
	 * Configuration reload must be enabled explicitly.
	 */
	@ConditionalOnProperty("spring.cloud.kubernetes.reload.enabled")
	@EnableScheduling
	@EnableAsync
	protected static class ConfigReloadAutoConfigurationBeans {

		@Autowired
		private AbstractEnvironment environment;

		@Autowired
		private KubernetesClient kubernetesClient;

		@Autowired
		private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

		@Autowired
		private SecretsPropertySourceLocator secretsPropertySourceLocator;

		/**
		 * Polling configMap ConfigurationChangeDetector.
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @param configMapPropertySourceLocator configMap property source locator
		 * @return a bean that listen to configuration changes and fire a reload.
		 */
		@Bean
		@ConditionalOnBean(ConfigMapPropertySourceLocator.class)
		@Conditional(PollingReloadDetectionMode.class)
		public ConfigurationChangeDetector configMapPropertyChangePollingWatcher(ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy, ConfigMapPropertySourceLocator configMapPropertySourceLocator) {

			return new PollingConfigMapChangeDetector(this.environment, properties, this.kubernetesClient, strategy,
					configMapPropertySourceLocator);
		}

		/**
		 * Polling secrets ConfigurationChangeDetector.
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @param secretsPropertySourceLocator secrets property source locator
		 * @return a bean that listen to configuration changes and fire a reload.
		 */
		@Bean
		@ConditionalOnBean(SecretsPropertySourceLocator.class)
		@Conditional(PollingReloadDetectionMode.class)
		public ConfigurationChangeDetector secretsPropertyChangePollingWatcher(ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy, SecretsPropertySourceLocator secretsPropertySourceLocator) {

			return new PollingSecretsChangeDetector(this.environment, properties, this.kubernetesClient, strategy,
					secretsPropertySourceLocator);
		}

		/**
		 * Event Based configMap ConfigurationChangeDetector.
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @param configMapPropertySourceLocator configMap property source locator
		 * @return a bean that listen to configMap change events and fire a reload.
		 */
		@Bean
		@ConditionalOnBean(ConfigMapPropertySourceLocator.class)
		@Conditional(EventReloadDetectionMode.class)
		public ConfigurationChangeDetector configMapPropertyChangeEventWatcher(ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy, ConfigMapPropertySourceLocator configMapPropertySourceLocator) {

			return new EventBasedConfigMapChangeDetector(this.environment, properties, this.kubernetesClient, strategy,
					configMapPropertySourceLocator);
		}

		/**
		 * Event Based secrets ConfigurationChangeDetector.
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @param secretsPropertySourceLocator secrets property source locator
		 * @return a bean that listen to secrets change events and fire a reload.
		 */
		@Bean
		@ConditionalOnBean(SecretsPropertySourceLocator.class)
		@Conditional(EventReloadDetectionMode.class)
		public ConfigurationChangeDetector secretsPropertyChangeEventWatcher(ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy, SecretsPropertySourceLocator secretsPropertySourceLocator) {

			return new EventBasedSecretsChangeDetector(this.environment, properties, this.kubernetesClient, strategy,
					secretsPropertySourceLocator);
		}

		/**
		 * @param properties config reload properties
		 * @param ctx application context
		 * @return provides the action to execute when the configuration changes.
		 */
		@Bean
		@ConditionalOnMissingBean
		public ConfigurationUpdateStrategy configurationUpdateStrategy(ConfigReloadProperties properties,
				ConfigurableApplicationContext ctx) {
			switch (properties.getStrategy()) {
			case SHUTDOWN:
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(), () -> {
					wait(properties);
					ctx.close();
				});
			}
			throw new IllegalStateException("Unsupported configuration update strategy: " + properties.getStrategy());
		}

		private static void wait(ConfigReloadProperties properties) {
			final long waitMillis = ThreadLocalRandom.current().nextLong(properties.getMaxWaitForRestart().toMillis());
			try {
				Thread.sleep(waitMillis);
			}
			catch (InterruptedException ignored) {
			}
		}

	}

}
