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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.MountConfigMapPropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
public final class ConfigReloadUtil {

	private ConfigReloadUtil() {

	}

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(ConfigReloadUtil.class));

	public static boolean reload(String target, String eventSourceType, PropertySourceLocator locator,
			ConfigurableEnvironment environment, Class<? extends MapPropertySource> existingSourcesType) {
		LOG.debug(() -> "onEvent " + target + ": " + eventSourceType);

		List<? extends MapPropertySource> sourceFromK8s = locateMapPropertySources(locator, environment);
		List<? extends MapPropertySource> existingSources = findPropertySources(existingSourcesType, environment);

		boolean changed = changed(sourceFromK8s, existingSources);
		if (changed) {
			LOG.info("Detected change in config maps/secrets");
			return true;
		}
		else {
			LOG.debug("No change detected in config maps/secrets, reload will not happen");
		}

		return false;
	}

	/**
	 * @param <S> property source type
	 * @param sourceClass class for which property sources will be found
	 * @return finds all registered property sources of the given type
	 */
	public static <S extends PropertySource<?>> List<S> findPropertySources(Class<S> sourceClass,
			ConfigurableEnvironment environment) {
		List<S> managedSources = new ArrayList<>();

		List<PropertySource<?>> sources = environment.getPropertySources().stream()
				.collect(Collectors.toCollection(ArrayList::new));
		LOG.debug(() -> "environment from findPropertySources: " + environment);
		LOG.debug(() -> "environment sources from findPropertySources : " + sources);

		while (!sources.isEmpty()) {
			PropertySource<?> source = sources.remove(0);
			if (source instanceof CompositePropertySource comp) {
				sources.addAll(comp.getPropertySources());
			}
			else if (sourceClass.isInstance(source)) {
				managedSources.add(sourceClass.cast(source));
			}
			else if (source instanceof MountConfigMapPropertySource mountConfigMapPropertySource) {
				// we know that the type is correct here
				managedSources.add((S) mountConfigMapPropertySource);
			}
			else if (source instanceof BootstrapPropertySource<?> bootstrapPropertySource) {
				PropertySource<?> propertySource = bootstrapPropertySource.getDelegate();
				LOG.debug(() -> "bootstrap delegate class : " + propertySource.getClass());
				if (sourceClass.isInstance(propertySource)) {
					sources.add(propertySource);
				}
				else if (propertySource instanceof MountConfigMapPropertySource mountConfigMapPropertySource) {
					// we know that the type is correct here
					managedSources.add((S) mountConfigMapPropertySource);
				}
			}
		}

		LOG.debug(() -> "findPropertySources : " + managedSources.stream().map(PropertySource::getName).toList());
		return managedSources;
	}

	/**
	 * Returns a list of MapPropertySource that correspond to the current state of the
	 * system. This only handles the PropertySource objects that are returned.
	 * @param propertySourceLocator Spring's property source locator
	 * @param environment Spring environment
	 * @return a list of MapPropertySource that correspond to the current state of the
	 * system
	 */
	static List<MapPropertySource> locateMapPropertySources(PropertySourceLocator propertySourceLocator,
			ConfigurableEnvironment environment) {

		List<MapPropertySource> result = new ArrayList<>();
		PropertySource<?> propertySource = propertySourceLocator.locate(environment);
		if (propertySource instanceof MapPropertySource mapPropertySource) {
			result.add(mapPropertySource);
		}
		else if (propertySource instanceof CompositePropertySource source) {

			source.getPropertySources().forEach(x -> {
				if (x instanceof MapPropertySource mapPropertySource) {
					result.add(mapPropertySource);
				}
			});
		}
		else {
			LOG.debug(() -> "Found property source that cannot be handled: " + propertySource.getClass());
		}

		LOG.debug(() -> "environment from locateMapPropertySources : " + environment);
		LOG.debug(() -> "sources from locateMapPropertySources : " + result);

		return result;
	}

	static boolean changed(List<? extends MapPropertySource> left, List<? extends MapPropertySource> right) {
		if (left.size() != right.size()) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("left size: " + left.size());
				left.forEach(item -> LOG.debug(item.toString()));

				LOG.debug("right size: " + right.size());
				right.forEach(item -> LOG.debug(item.toString()));
			}
			LOG.warn(() -> "The current number of ConfigMap PropertySources does not match "
					+ "the ones loaded from Kubernetes - No reload will take place");
			return false;
		}

		for (int i = 0; i < left.size(); i++) {
			MapPropertySource leftPropertySource = left.get(i);
			MapPropertySource rightPropertySource = right.get(i);
			if (changed(leftPropertySource, rightPropertySource)) {
				LOG.debug(() -> "found change in : " + leftPropertySource);
				return true;
			}
		}
		LOG.debug(() -> "no changes found, reload will not happen");
		return false;
	}

	/**
	 * Determines if two property sources are different.
	 * @param left left map property sources
	 * @param right right map property sources
	 * @return {@code true} if source has changed
	 */
	static boolean changed(MapPropertySource left, MapPropertySource right) {
		if (left == right) {
			return false;
		}
		if (left == null || right == null) {
			return true;
		}
		Map<String, Object> leftMap = left.getSource();
		Map<String, Object> rightMap = right.getSource();
		return !Objects.equals(leftMap, rightMap);
	}

}
