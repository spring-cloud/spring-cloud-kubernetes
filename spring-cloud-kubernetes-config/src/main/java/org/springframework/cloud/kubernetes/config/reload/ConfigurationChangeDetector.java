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

package org.springframework.cloud.kubernetes.config.reload;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * This is the superclass of all beans that can listen to changes in the configuration and
 * fire a reload.
 *
 * @author Nicola Ferraro
 */
public abstract class ConfigurationChangeDetector {

	protected Log log = LogFactory.getLog(getClass());

	protected ConfigurableEnvironment environment;

	protected ConfigReloadProperties properties;

	protected KubernetesClient kubernetesClient;

	protected ConfigurationUpdateStrategy strategy;

	public ConfigurationChangeDetector(ConfigurableEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy) {
		this.environment = environment;
		this.properties = properties;
		this.kubernetesClient = kubernetesClient;
		this.strategy = strategy;
	}

	@PreDestroy
	public void shutdown() {
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		this.kubernetesClient.close();
	}

	public void reloadProperties() {
		this.log.info("Reloading using strategy: " + this.strategy.getName());
		this.strategy.reload();
	}

	/**
	 * Determines if two property sources are different.
	 * @param left left map property sources
	 * @param right right map property sources
	 * @return {@code true} if source has changed
	 */
	protected boolean changed(MapPropertySource left, MapPropertySource right) {
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

	protected boolean changed(List<? extends MapPropertySource> left, List<? extends MapPropertySource> right) {

		if (left.size() != right.size()) {
			this.log.warn("The current number of ConfigMap PropertySources does not match "
					+ "the ones loaded from the Kubernetes - No reload will take place");
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
	 * Finds one registered property source of the given type, logging a warning if
	 * multiple property sources of that type are available.
	 * @param <S> property source type
	 * @param sourceClass class for which property sources will be searched for
	 * @return matched property source
	 */
	protected <S extends PropertySource<?>> S findPropertySource(Class<S> sourceClass) {
		List<S> sources = findPropertySources(sourceClass);
		if (sources.size() == 0) {
			return null;
		}
		if (sources.size() > 1) {
			this.log.warn("Found more than one property source of type " + sourceClass);
		}
		return sources.get(0);
	}

	/**
	 * @param <S> property source type
	 * @param sourceClass class for which property sources will be found
	 * @return finds all registered property sources of the given type
	 */
	protected <S extends PropertySource<?>> List<S> findPropertySources(Class<S> sourceClass) {
		List<S> managedSources = new LinkedList<>();

		LinkedList<PropertySource<?>> sources = toLinkedList(this.environment.getPropertySources());
		while (!sources.isEmpty()) {
			PropertySource<?> source = sources.pop();
			if (source instanceof CompositePropertySource) {
				CompositePropertySource comp = (CompositePropertySource) source;
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

	private <E> LinkedList<E> toLinkedList(Iterable<E> it) {
		LinkedList<E> list = new LinkedList<>();
		for (E e : it) {
			list.add(e);
		}
		return list;
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
		else if (propertySource instanceof CompositePropertySource) {
			result.addAll(((CompositePropertySource) propertySource).getPropertySources().stream()
					.filter(p -> p instanceof MapPropertySource).map(p -> (MapPropertySource) p)
					.collect(Collectors.toList()));
		}
		else {
			this.log.debug("Found property source that cannot be handled: " + propertySource.getClass());
		}

		return result;
	}

}
