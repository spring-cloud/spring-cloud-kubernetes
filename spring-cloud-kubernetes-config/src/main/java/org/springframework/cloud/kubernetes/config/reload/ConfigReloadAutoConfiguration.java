/*
 * Copyright 2013-2019 the original author or authors.
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
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

/**
 * Definition of beans needed for the automatic reload of configuration.
 *
 * @author Nicolla Ferraro
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@ConditionalOnClass(EndpointAutoConfiguration.class)
@AutoConfigureAfter({ InfoEndpointAutoConfiguration.class,
		RefreshEndpointAutoConfiguration.class, RefreshAutoConfiguration.class })
@EnableConfigurationProperties(ConfigReloadProperties.class)

public class ConfigReloadAutoConfiguration {

	/**
	 * Configuration reload must be enabled explicitly.
	 */
	@ConditionalOnProperty("spring.cloud.kubernetes.reload.enabled")
	@ConditionalOnClass({ RestartEndpoint.class, ContextRefresher.class })
	@EnableScheduling
	@EnableAsync
	protected static class ConfigReloadAutoConfigurationBeans {

		@Autowired
		private AbstractEnvironment environment;

		@Autowired
		private KubernetesClient kubernetesClient;

		/**
		 * @param properties config reload properties
		 * @param strategy configuration update strategy
		 * @return a bean that listen to configuration changes and fire a reload.
		 */
		@Bean
		@Conditional(OnConfigEnabledOrSecretsEnabled.class)
		public ConfigurationChangeDetector propertyChangeWatcher(
				ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
				@Autowired(
						required = false) ConfigMapPropertySourceLocator configMapPropertySourceLocator,
				@Autowired(
						required = false) SecretsPropertySourceLocator secretsPropertySourceLocator) {
			switch (properties.getMode()) {
			case POLLING:
				return new PollingConfigurationChangeDetector(this.environment,
						properties, this.kubernetesClient, strategy,
						configMapPropertySourceLocator, secretsPropertySourceLocator);
			case EVENT:
				return new EventBasedConfigurationChangeDetector(this.environment,
						properties, this.kubernetesClient, strategy,
						configMapPropertySourceLocator, secretsPropertySourceLocator);
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
				ConfigReloadProperties properties, ConfigurableApplicationContext ctx,
				@Autowired(required = false) RestartEndpoint restarter,
				ContextRefresher refresher) {
			switch (properties.getStrategy()) {
			case RESTART_CONTEXT:
				Assert.notNull(restarter, "Restart endpoint is not enabled");
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(),
						() -> {
							wait(properties);
							restarter.restart();
						});
			case REFRESH:
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(),
						refresher::refresh);
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

		private static class OnConfigEnabledOrSecretsEnabled extends AnyNestedCondition {

			OnConfigEnabledOrSecretsEnabled() {
				super(ConfigurationPhase.REGISTER_BEAN);
			}

			@ConditionalOnBean(ConfigMapPropertySourceLocator.class)
			static class configEnabled {

			}

			@ConditionalOnBean(SecretsPropertySourceLocator.class)
			static class secretsEnabled {

			}

		}

	}

}
