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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * Kubernetes {@link PropertySourceLocator} for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author wind57
 * @author Isik Erhan
 */
public abstract class SecretsPropertySourceLocator implements PropertySourceLocator {

	private static final Log LOG = LogFactory.getLog(SecretsPropertySourceLocator.class);

	private final SecretsCache cache;

	protected final SecretsConfigProperties properties;

	/**
	 * This constructor is deprecated, and we do not use it anymore internally. It will be
	 * removed in the next major release.
	 */
	@Deprecated(forRemoval = true)
	public SecretsPropertySourceLocator(SecretsConfigProperties properties) {
		this.properties = properties;
		this.cache = new SecretsCache.NOOPCache();
	}

	public SecretsPropertySourceLocator(SecretsConfigProperties properties, SecretsCache cache) {
		this.properties = properties;
		this.cache = cache;
	}

	@Override
	public PropertySource<?> locate(Environment environment) {
		if (environment instanceof ConfigurableEnvironment env) {

			List<NormalizedSource> sources = this.properties.determineSources(environment);
			Set<NormalizedSource> uniqueSources = new HashSet<>(sources);
			LOG.debug("Secrets normalized sources : " + sources);
			CompositePropertySource composite = new CompositePropertySource("composite-secrets");

			if (this.properties.enableApi()) {
				uniqueSources
						.forEach(s -> composite.addPropertySource(getSecretsPropertySourceForSingleSecret(env, s)));
			}

			cache.discardAll();
			return composite;
		}
		return null;
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return PropertySourceLocator.super.locateCollection(environment);
	}

	private SecretsPropertySource getSecretsPropertySourceForSingleSecret(ConfigurableEnvironment environment,
			NormalizedSource normalizedSource) {

		return getPropertySource(environment, normalizedSource);
	}

	protected abstract SecretsPropertySource getPropertySource(ConfigurableEnvironment environment,
			NormalizedSource normalizedSource);

}
