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
			// read for secrets mount
			putPathConfig(composite);

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

	protected void putPathConfig(CompositePropertySource composite) {

		if (!properties.paths().isEmpty()) {
			LOG.warn(
					"path support is deprecated and will be removed in a future release. Please use spring.config.import");
		}

		this.properties.paths().stream().map(Paths::get).filter(Files::exists).flatMap(x -> {
			try {
				return Files.walk(x);
			}
			catch (IOException e) {
				LOG.warn("Error walking properties files", e);
				return null;
			}
		}).filter(Objects::nonNull).filter(Files::isRegularFile).collect(new SecretsPropertySourceCollector())
				.forEach(composite::addPropertySource);
	}

	/**
	 * @author wind57
	 */
	private static class SecretsPropertySourceCollector
			implements Collector<Path, List<SecretsPropertySource>, List<SecretsPropertySource>> {

		@Override
		public Supplier<List<SecretsPropertySource>> supplier() {
			return ArrayList::new;
		}

		@Override
		public BiConsumer<List<SecretsPropertySource>, Path> accumulator() {
			return (list, filePath) -> {
				SecretsPropertySource source = property(filePath);
				if (source != null) {
					list.add(source);
				}
			};
		}

		@Override
		public BinaryOperator<List<SecretsPropertySource>> combiner() {
			return (left, right) -> {
				left.addAll(right);
				return left;
			};
		}

		@Override
		public Function<List<SecretsPropertySource>, List<SecretsPropertySource>> finisher() {
			return Function.identity();
		}

		@Override
		public Set<Characteristics> characteristics() {
			return EnumSet.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
		}

		private SecretsPropertySource property(Path filePath) {

			String fileName = filePath.getFileName().toString();

			try {
				String content = new String(Files.readAllBytes(filePath)).trim();
				String sourceName = fileName.toLowerCase();
				SourceData sourceData = new SourceData(sourceName, Collections.singletonMap(fileName, content));
				return new SecretsPropertySource(sourceData);
			}
			catch (IOException e) {
				LOG.warn("Error reading properties file", e);
				return null;
			}
		}

	}

}
