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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.log.LogAccessor;

/**
 * This is the superclass of all beans that can listen to changes in the configuration and
 * fire a reload.
 *
 * @author Nicola Ferraro
 */
public abstract class ConfigurationChangeDetector {

	protected LogAccessor log = new LogAccessor(LogFactory.getLog(this.getClass()));

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
		log.info(() -> "Reloading using strategy: " + this.strategy.name());
		strategy.reloadProcedure().run();
	}

	public boolean changed(List<? extends MapPropertySource> left, List<? extends MapPropertySource> right) {
		if (left.size() != right.size()) {
			log.warn(() -> "The current number of ConfigMap PropertySources does not match "
					+ "the ones loaded from the Kubernetes - No reload will take place");

			if (log.isDebugEnabled()) {
				log.debug("left size: " + left.size());
				left.forEach(item -> log.debug(item.toString()));

				log.debug("right size: " + right.size());
				right.forEach(item -> log.debug(item.toString()));
			}
			return false;
		}

		for (int i = 0; i < left.size(); i++) {
			if (changed(left.get(i), right.get(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param <S> property source type
	 * @param sourceClass class for which property sources will be found
	 * @return finds all registered property sources of the given type
	 */
	public <S extends PropertySource<?>> List<S> findPropertySources(Class<S> sourceClass) {
		List<S> managedSources = new ArrayList<>();

		List<PropertySource<?>> sources = environment.getPropertySources().stream()
				.collect(Collectors.toCollection(ArrayList::new));
		log.debug(() -> "environment: " + environment);
		log.debug(() -> "environment sources: " + sources);

		while (!sources.isEmpty()) {
			PropertySource<?> source = sources.remove(0);
			if (source instanceof CompositePropertySource comp) {
				sources.addAll(comp.getPropertySources());
			}
			else if (sourceClass.isInstance(source)) {
				managedSources.add(sourceClass.cast(source));
			}
			else if (source instanceof BootstrapPropertySource) {
				PropertySource<?> propertySource = ((BootstrapPropertySource<?>) source).getDelegate();
				if (sourceClass.isInstance(propertySource)) {
					sources.add(propertySource);
				}
			}
		}

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
	protected List<MapPropertySource> locateMapPropertySources(PropertySourceLocator propertySourceLocator,
			Environment environment) {

		List<MapPropertySource> result = new ArrayList<>();
		PropertySource<?> propertySource = propertySourceLocator.locate(environment);
		if (propertySource instanceof MapPropertySource) {
			result.add((MapPropertySource) propertySource);
		}
		else if (propertySource instanceof CompositePropertySource source) {

			List<MapPropertySource> list = source.getPropertySources().stream()
					.filter(p -> p instanceof MapPropertySource).map(x -> (MapPropertySource) x)
					.collect(Collectors.toList());
			result.addAll(list);
		}
		else {
			log.debug(() ->"Found property source that cannot be handled: " + propertySource.getClass());
		}

		log.debug(() -> "environment: " + environment);
		log.debug(() -> "sources: " + result);

		return result;
	}

	/**
	 * Determines if two property sources are different.
	 * @param left left map property sources
	 * @param right right map property sources
	 * @return {@code true} if source has changed
	 */
	boolean changed(MapPropertySource left, MapPropertySource right) {
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
