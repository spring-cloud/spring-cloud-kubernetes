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

package org.springframework.cloud.kubernetes.configuration.watcher;

import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.reload.enabled", havingValue = "false", matchIfMissing = true)
class ConfigUpdateStrategyAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(ConfigUpdateStrategyAutoConfiguration.class));

	@Bean
	@ConditionalOnMissingBean
	ConfigurationUpdateStrategy noopConfigurationUpdateStrategy() {
		LOG.debug(() -> "creating NOOP strategy because reload is disabled");
		return ConfigurationUpdateStrategy.NOOP;
	}

}
