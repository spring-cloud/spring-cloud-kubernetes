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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Ryan Baxter
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
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @return a bean that listen to configuration changes and fire a reload.
		 */
		@Bean
		@ConditionalOnMissingBean
		public ConfigurationChangeDetector propertyChangeWatcher(
				ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy) {
			switch (properties.getMode()) {
			case POLLING:
				return new PollingConfigurationChangeDetector(this.environment,
						properties, this.kubernetesClient, strategy,
						this.configMapPropertySourceLocator,
						this.secretsPropertySourceLocator);
			case EVENT:
				return new EventBasedConfigurationChangeDetector(this.environment,
						properties, this.kubernetesClient, strategy,
						this.configMapPropertySourceLocator,
						this.secretsPropertySourceLocator);
			}
			throw new IllegalStateException(
					"Unsupported configuration reload mode: " + properties.getMode());
		}

		/**
		 * @param properties config reload properties
		 * @param ctx application context
		 * @param restarter restart endpoint
		 * @param refresher context refresher
		 * @return provides the action to execute when the configuration changes.
		 */
		@Bean
		@ConditionalOnMissingBean
		public ConfigurationUpdateStrategy configurationUpdateStrategy(
				ConfigReloadProperties properties, ConfigurableApplicationContext ctx) {
			switch (properties.getStrategy()) {
			case SHUTDOWN:
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(),
						() -> {
							wait(properties);
							ctx.close();
						});
			}
			throw new IllegalStateException("Unsupported configuration update strategy: "
					+ properties.getStrategy());
		}

		private static void wait(ConfigReloadProperties properties) {
			final long waitMillis = ThreadLocalRandom.current()
					.nextLong(properties.getMaxWaitForRestart().toMillis());
			try {
				Thread.sleep(waitMillis);
			}
			catch (InterruptedException ignored) {
			}
		}

	}

}
