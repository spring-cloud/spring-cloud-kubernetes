/*
 * Copyright 2013-present the original author or authors.
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

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
abstract class CommonPropertySourceLocator implements PropertySourceLocator {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(CommonPropertySourceLocator.class));

	protected final SourceConfigProperties properties;

	private final String type;

	CommonPropertySourceLocator(SourceConfigProperties properties, String type) {
		this.properties = properties;
		this.type = type;
	}

	protected abstract MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			NormalizedSource normalizedSource, ReadType readType);

	@Override
	public PropertySource<?> locate(Environment environment) {

		if (environment instanceof ConfigurableEnvironment env) {

			List<NormalizedSource> sources = properties.determineSources(false, environment);
			Set<NormalizedSource> uniqueSources = new HashSet<>(sources);
			LOG.debug(type + " normalized sources : " + uniqueSources);
			CompositePropertySource composite = new CompositePropertySource("composite-" + type);

			uniqueSources.forEach(secretSource -> {
				MapPropertySource propertySource = getPropertySource(env, secretSource, properties.readType());

				if ("true".equals(propertySource.getProperty(Constants.ERROR_PROPERTY))) {
					LOG.warn(() -> "Failed to load source: " + secretSource);
				}
				else {
					LOG.debug("Adding " + type + " property source " + propertySource.getName());
					composite.addFirstPropertySource(propertySource);
				}
			});

			return composite;
		}
		return null;
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return PropertySourceLocator.super.locateCollection(environment);
	}

}
