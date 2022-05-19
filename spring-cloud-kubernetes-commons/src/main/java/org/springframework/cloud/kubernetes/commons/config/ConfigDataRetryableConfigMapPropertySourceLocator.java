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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Collection;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.retry.support.RetryTemplate;

/**
 * ConfigMapPropertySourceLocator for when retry is enabled.
 *
 * @author Ryan Baxter
 */
public class ConfigDataRetryableConfigMapPropertySourceLocator extends ConfigMapPropertySourceLocator {

	private RetryTemplate retryTemplate;

	private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	public ConfigDataRetryableConfigMapPropertySourceLocator(
			ConfigMapPropertySourceLocator configMapPropertySourceLocator, ConfigMapConfigProperties properties) {
		super(properties);
		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
		this.retryTemplate = RetryTemplate.builder().maxAttempts(properties.getRetry().getMaxAttempts())
				.exponentialBackoff(properties.getRetry().getInitialInterval(), properties.getRetry().getMultiplier(),
						properties.getRetry().getMaxInterval())
				.build();
	}

	@Override
	protected MapPropertySource getMapPropertySource(NormalizedSource normalizedSource,
			ConfigurableEnvironment environment) {
		return configMapPropertySourceLocator.getMapPropertySource(normalizedSource, environment);
	}

	@Override
	public PropertySource<?> locate(Environment environment) {
		return retryTemplate.execute(retryContext -> configMapPropertySourceLocator.locate(environment));
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return retryTemplate.execute(retryContext -> configMapPropertySourceLocator.locateCollection(environment));
	}

	public void setConfigMapPropertySourceLocator(ConfigMapPropertySourceLocator configMapPropertySourceLocator) {
		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
	}

	public ConfigMapPropertySourceLocator getConfigMapPropertySourceLocator() {
		return configMapPropertySourceLocator;
	}

}
