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

package org.springframework.cloud.kubernetes.commons.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
public abstract class SecretsPropertySourceLocator implements PropertySourceLocator {

	private static final Log LOG = LogFactory.getLog(SecretsPropertySourceLocator.class);

	protected final SecretsConfigProperties properties;

	public SecretsPropertySourceLocator(SecretsConfigProperties properties) {
		this.properties = properties;
	}

	@Override
	public PropertySource locate(Environment environment) {
		if (environment instanceof ConfigurableEnvironment) {
			ConfigurableEnvironment env = (ConfigurableEnvironment) environment;

			List<SecretsConfigProperties.NormalizedSource> sources = this.properties.determineSources();
			CompositePropertySource composite = new CompositePropertySource("composite-secrets");
			if (this.properties.isEnableApi()) {
				sources.forEach(
						s -> composite.addFirstPropertySource(getKubernetesPropertySourceForSingleSecret(env, s)));
			}

			// read for secrets mount
			putPathConfig(composite);

			return composite;
		}
		return null;
	}

	private MapPropertySource getKubernetesPropertySourceForSingleSecret(ConfigurableEnvironment environment,
			SecretsConfigProperties.NormalizedSource normalizedSource) {

		String configurationTarget = this.properties.getConfigurationTarget();
		return getPropertySource(environment, normalizedSource, configurationTarget);
	}

	protected abstract MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			SecretsConfigProperties.NormalizedSource normalizedSource, String configurationTarget);

	protected void putPathConfig(CompositePropertySource composite) {
		this.properties.getPaths().stream().map(Paths::get).filter(Files::exists).forEach(p -> putAll(p, composite));
	}

	protected void putAll(Path path, CompositePropertySource composite) {
		try {

			Files.walk(path).filter(Files::isRegularFile).forEach(p -> readFile(p, composite));
		}
		catch (IOException e) {
			LOG.warn("Error walking properties files", e);
		}
	}

	protected void readFile(Path path, CompositePropertySource composite) {
		try {
			Map<String, Object> result = new HashMap<>();
			result.put(path.getFileName().toString(), new String(Files.readAllBytes(path)).trim());
			if (!result.isEmpty()) {
				composite.addFirstPropertySource(
						new MapPropertySource(path.getFileName().toString().toLowerCase(), result));
			}
		}
		catch (IOException e) {
			LOG.warn("Error reading properties file", e);
		}
	}

}
