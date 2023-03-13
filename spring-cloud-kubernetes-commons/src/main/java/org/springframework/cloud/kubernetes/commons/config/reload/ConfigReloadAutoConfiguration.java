/*
 * Copyright 2013-2022 the original author or authors.
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

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.commons.util.TaskSchedulerWrapper;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.kubernetes.commons.config.ConditionalOnKubernetesAndConfigEnabled;
import org.springframework.cloud.kubernetes.commons.config.reload.condition.ConditionalOnKubernetesReloadEnabled;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesAndConfigEnabled
@ConditionalOnKubernetesReloadEnabled
@ConditionalOnClass({ EndpointAutoConfiguration.class, RestartEndpoint.class, ContextRefresher.class })
@AutoConfigureAfter({ InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
		RefreshAutoConfiguration.class })
public class ConfigReloadAutoConfiguration {

	@Bean("springCloudKubernetesTaskScheduler")
	@ConditionalOnMissingBean
	public TaskSchedulerWrapper<TaskScheduler> taskScheduler() {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		threadPoolTaskScheduler.setThreadNamePrefix("spring-cloud-kubernetes-ThreadPoolTaskScheduler-");
		threadPoolTaskScheduler.setDaemon(true);

		return new TaskSchedulerWrapper<>(threadPoolTaskScheduler);
	}

	@Bean
	@ConditionalOnMissingBean
	public ConfigurationUpdateStrategy configurationUpdateStrategy(ConfigReloadProperties properties,
			ConfigurableApplicationContext ctx, Optional<RestartEndpoint> restarter, ContextRefresher refresher) {
		String strategyName = properties.strategy().name();
		return switch (properties.strategy()) {
			case RESTART_CONTEXT -> {
				restarter.orElseThrow(() -> new AssertionError("Restart endpoint is not enabled"));
				yield new ConfigurationUpdateStrategy(strategyName, () -> {
					wait(properties);
					restarter.get().restart();
				});
			}
			case REFRESH -> new ConfigurationUpdateStrategy(strategyName, refresher::refresh);
			case SHUTDOWN -> new ConfigurationUpdateStrategy(strategyName, () -> {
				wait(properties);
				ctx.close();
			});
		};
	}

	private static void wait(ConfigReloadProperties properties) {
		long waitMillis = ThreadLocalRandom.current().nextLong(properties.maxWaitForRestart().toMillis());
		try {
			Thread.sleep(waitMillis);
		}
		catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

}
