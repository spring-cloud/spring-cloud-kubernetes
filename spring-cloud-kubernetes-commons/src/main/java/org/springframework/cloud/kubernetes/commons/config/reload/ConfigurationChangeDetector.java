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

import java.util.Objects;

import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.log.LogAccessor;

/**
 * This is the superclass of all beans that can listen to changes in the configuration and
 * fire a reload.
 *
 * @author Nicola Ferraro
 */
public abstract class ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(ConfigurationChangeDetector.class));

	protected ConfigurableEnvironment environment;

	protected ConfigReloadProperties properties;

	protected ConfigurationUpdateStrategy strategy;

	public ConfigurationChangeDetector(ConfigurableEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy) {
		this.environment = Objects.requireNonNull(environment);
		this.properties = Objects.requireNonNull(properties);
		this.strategy = Objects.requireNonNull(strategy);
	}

	public void reloadProperties() {
		LOG.info(() -> "Reloading using strategy: " + this.strategy.name());
		strategy.reloadProcedure().run();
	}

}
