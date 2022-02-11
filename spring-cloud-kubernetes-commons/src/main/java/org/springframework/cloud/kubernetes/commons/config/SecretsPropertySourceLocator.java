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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
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

	protected final SecretsConfigProperties properties;

	public SecretsPropertySourceLocator(SecretsConfigProperties properties) {
		this.properties = properties;
	}

	@Override
	public PropertySource<?> locate(Environment environment) {
		if (environment instanceof ConfigurableEnvironment) {
			ConfigurableEnvironment env = (ConfigurableEnvironment) environment;

			List<SecretsConfigProperties.NormalizedSource> sources = this.properties.determineSources();
			Set<SecretsConfigProperties.NormalizedSource> uniqueSources = new HashSet<>(sources);
			LOG.debug("Secrets normalized sources : " + sources);
			CompositePropertySource composite = new CompositePropertySource("composite-secrets");
			// read for secrets mount
			putPathConfig(composite);

			if (this.properties.isEnableApi()) {
				uniqueSources.forEach(s -> composite.addPropertySource(getMapPropertySourceForSingleSecret(env, s)));
			}

			return composite;
		}
		return null;
	}

	@Override
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return PropertySourceLocator.super.locateCollection(environment);
	}

	private MapPropertySource getMapPropertySourceForSingleSecret(ConfigurableEnvironment environment,
			SecretsConfigProperties.NormalizedSource normalizedSource) {

		String configurationTarget = this.properties.getConfigurationTarget();
		return getPropertySource(environment, normalizedSource, configurationTarget);
	}

	protected abstract MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			SecretsConfigProperties.NormalizedSource normalizedSource, String configurationTarget);

	protected void putPathConfig(CompositePropertySource composite) {

		this.properties.getPaths().stream().map(Paths::get).filter(Files::exists).flatMap(x -> {
			try {
				return Files.walk(x);
			}
			catch (IOException e) {
				LOG.warn("Error walking properties files", e);
				return null;
			}
		}).filter(Objects::nonNull).filter(Files::isRegularFile).collect(new MapPropertySourceCollector())
				.forEach(composite::addPropertySource);
	}

	/**
	 * @author wind57
	 */
	private static class MapPropertySourceCollector
			implements Collector<Path, List<MapPropertySource>, List<MapPropertySource>> {

		@Override
		public Supplier<List<MapPropertySource>> supplier() {
			return ArrayList::new;
		}

		@Override
		public BiConsumer<List<MapPropertySource>, Path> accumulator() {
			return (list, filePath) -> {
				MapPropertySource source = property(filePath);
				if (source != null) {
					list.add(source);
				}
			};
		}

		@Override
		public BinaryOperator<List<MapPropertySource>> combiner() {
			return (left, right) -> {
				left.addAll(right);
				return left;
			};
		}

		@Override
		public Function<List<MapPropertySource>, List<MapPropertySource>> finisher() {
			return Function.identity();
		}

		@Override
		public Set<Characteristics> characteristics() {
			return EnumSet.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
		}

		private MapPropertySource property(Path filePath) {

			String fileName = filePath.getFileName().toString();

			try {
				String content = new String(Files.readAllBytes(filePath)).trim();
				return new MapPropertySource(fileName.toLowerCase(), Collections.singletonMap(fileName, content));
			}
			catch (IOException e) {
				LOG.warn("Error reading properties file", e);
				return null;
			}
		}

	}

}
