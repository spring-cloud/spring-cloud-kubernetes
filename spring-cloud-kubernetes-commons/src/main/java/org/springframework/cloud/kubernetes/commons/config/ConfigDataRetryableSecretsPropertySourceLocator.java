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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Collection;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.retry.support.RetryTemplate;

/**
 * SecretsPropertySourceLocator for when retry is enabled.
 *
 * @author Ryan Baxter
 */
public class ConfigDataRetryableSecretsPropertySourceLocator extends SecretsPropertySourceLocator {

	private RetryTemplate retryTemplate;

	private SecretsPropertySourceLocator secretsPropertySourceLocator;

	public ConfigDataRetryableSecretsPropertySourceLocator(SecretsPropertySourceLocator propertySourceLocator,
			SecretsConfigProperties secretsConfigProperties) {
		super(secretsConfigProperties);
		this.secretsPropertySourceLocator = propertySourceLocator;
		this.retryTemplate = RetryTemplate.builder().maxAttempts(properties.getRetry().getMaxAttempts())
				.exponentialBackoff(properties.getRetry().getInitialInterval(), properties.getRetry().getMultiplier(),
						properties.getRetry().getMaxInterval())
				.build();
	}

	@Override
	public PropertySource<?> locate(Environment environment) {
		return retryTemplate.execute(retryContext -> secretsPropertySourceLocator.locate(environment));
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return retryTemplate.execute(retryContext -> secretsPropertySourceLocator.locateCollection(environment));
	}

	@Override
	protected MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			NormalizedSource normalizedSource) {
		return this.secretsPropertySourceLocator.getPropertySource(environment, normalizedSource);
	}

	public SecretsPropertySourceLocator getSecretsPropertySourceLocator() {
		return secretsPropertySourceLocator;
	}

	public void setSecretsPropertySourceLocator(SecretsPropertySourceLocator secretsPropertySourceLocator) {
		this.secretsPropertySourceLocator = secretsPropertySourceLocator;
	}

}
