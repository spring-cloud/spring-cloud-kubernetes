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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.commons.util.TaskSchedulerWrapper;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.kubernetes.commons.config.ConditionalOnKubernetesAndConfigEnabled;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesAndConfigEnabled
@ConditionalOnClass(EndpointAutoConfiguration.class)
@AutoConfigureAfter({ InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
		RefreshAutoConfiguration.class })
public class ConfigReloadAutoConfiguration {

	/**
	 * Configuration reload must be enabled explicitly.
	 */
	@ConditionalOnProperty("spring.cloud.kubernetes.reload.enabled")
	@ConditionalOnClass({ RestartEndpoint.class, ContextRefresher.class })
	protected static class ConfigReloadAutoConfigurationBeans {

		@Bean("springCloudKubernetesTaskScheduler")
		@ConditionalOnMissingBean
		public TaskSchedulerWrapper taskScheduler() {
			ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

			threadPoolTaskScheduler.setThreadNamePrefix("spring-cloud-kubernetes-ThreadPoolTaskScheduler-");
			threadPoolTaskScheduler.setDaemon(true);

			return new TaskSchedulerWrapper(threadPoolTaskScheduler);
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
		public ConfigurationUpdateStrategy configurationUpdateStrategy(ConfigReloadProperties properties,
				ConfigurableApplicationContext ctx, @Autowired(required = false) RestartEndpoint restarter,
				ContextRefresher refresher) {
			switch (properties.getStrategy()) {
			case RESTART_CONTEXT:
				Assert.notNull(restarter, "Restart endpoint is not enabled");
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(), () -> {
					wait(properties);
					restarter.restart();
				});
			case REFRESH:
				return new ConfigurationUpdateStrategy(properties.getStrategy().name(), refresher::refresh);
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
