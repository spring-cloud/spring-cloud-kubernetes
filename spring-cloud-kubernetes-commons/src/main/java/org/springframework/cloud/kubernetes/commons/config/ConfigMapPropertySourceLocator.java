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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * A {@link PropertySourceLocator} that uses config maps.
 *
 * @author Ioannis Canellos
 * @author Michael Moudatsos
 * @author Isik Erhan
 */
public abstract class ConfigMapPropertySourceLocator implements PropertySourceLocator {

	private static final Log LOG = LogFactory.getLog(ConfigMapPropertySourceLocator.class);

	private final ConfigMapCache cache;

	protected final ConfigMapConfigProperties properties;

	/**
	 * This constructor is deprecated, and we do not use it anymore internally. It will be
	 * removed in the next major release.
	 */
	@Deprecated(forRemoval = true)
	public ConfigMapPropertySourceLocator(ConfigMapConfigProperties properties) {
		this.properties = properties;
		this.cache = new ConfigMapCache.NOOPCache();
	}

	public ConfigMapPropertySourceLocator(ConfigMapConfigProperties properties, ConfigMapCache cache) {
		this.properties = properties;
		this.cache = cache;
	}

	protected abstract MapPropertySource getMapPropertySource(NormalizedSource normalizedSource,
			ConfigurableEnvironment environment);

	@Override
	public PropertySource<?> locate(Environment environment) {
		if (environment instanceof ConfigurableEnvironment env) {

			CompositePropertySource composite = new CompositePropertySource("composite-configmap");
			if (this.properties.enableApi()) {
				Set<NormalizedSource> sources = new LinkedHashSet<>(this.properties.determineSources(environment));
				LOG.debug("Config Map normalized sources : " + sources);
				sources.forEach(s -> composite.addFirstPropertySource(getMapPropertySource(s, env)));
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
}
