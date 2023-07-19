/*
 * Copyright 2013-2021 the original author or authors.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.yamlParserGenerator;

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

			addPropertySourcesFromPaths(environment, composite);

			cache.discardAll();
			return composite;
		}
		return null;
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return PropertySourceLocator.super.locateCollection(environment);
	}

	private void addPropertySourcesFromPaths(Environment environment, CompositePropertySource composite) {
		Set<String> uniquePaths = new LinkedHashSet<>(properties.paths());
		if (!uniquePaths.isEmpty()) {
			LOG.warn(
					"path support is deprecated and will be removed in a future release. Please use spring.config.import");
		}
		LOG.debug("paths property sources : " + uniquePaths);
		uniquePaths.stream().map(Paths::get).filter(p -> {
			boolean exists = Files.exists(p);
			if (!exists) {
				LOG.warn("Configured input path: " + p
						+ " will be ignored because it does not exist on the file system");
			}
			return exists;
		}).filter(p -> {
			boolean regular = Files.isRegularFile(p);
			if (!regular) {
				LOG.warn("Configured input path: " + p + " will be ignored because it is not a regular file");
			}
			return regular;
		}).toList().forEach(p -> {
			try {
				String content = new String(Files.readAllBytes(p)).trim();
				String filename = p.toAbsolutePath().toString().toLowerCase();
				if (filename.endsWith(".properties")) {
					addPropertySourceIfNeeded(c -> PROPERTIES_TO_MAP.apply(KEY_VALUE_TO_PROPERTIES.apply(c)), content,
							filename, composite);
				}
				else if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
					addPropertySourceIfNeeded(c -> PROPERTIES_TO_MAP.apply(yamlParserGenerator(environment).apply(c)),
							content, filename, composite);
				}
			}
			catch (IOException e) {
				LOG.warn("Error reading input file", e);
			}
		});
	}

	private void addPropertySourceIfNeeded(Function<String, Map<String, Object>> contentToMapFunction, String content,
			String name, CompositePropertySource composite) {

		Map<String, Object> map = new HashMap<>(contentToMapFunction.apply(content));
		if (map.isEmpty()) {
			LOG.warn("Property source: " + name + "will be ignored because no properties could be found");
		}
		else {
			LOG.debug("will add file-based property source : " + name);
			composite.addFirstPropertySource(new MountConfigMapPropertySource(name, map));
		}
	}

}
