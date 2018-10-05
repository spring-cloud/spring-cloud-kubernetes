/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import static org.springframework.cloud.kubernetes.config.ConfigUtils.getApplicationName;
import static org.springframework.cloud.kubernetes.config.ConfigUtils.getApplicationNamespace;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.yamlParserGenerator;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.config.ConfigMapConfigProperties.NormalizedSource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Order(0)
public class ConfigMapPropertySourceLocator implements PropertySourceLocator {

	private static final Log LOG = LogFactory.getLog(ConfigMapPropertySourceLocator.class);

	private final KubernetesClient client;
	private final ConfigMapConfigProperties properties;

	public ConfigMapPropertySourceLocator(KubernetesClient client,
			ConfigMapConfigProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public PropertySource locate(Environment environment) {
		if (environment instanceof ConfigurableEnvironment) {
			ConfigurableEnvironment env = (ConfigurableEnvironment) environment;

			List<ConfigMapConfigProperties.NormalizedSource> sources = properties
					.determineSources();
			CompositePropertySource composite = new CompositePropertySource(
					"composite-configmap");
			if (properties.isEnableApi()) {
				sources.forEach(s -> composite.addFirstPropertySource(
						getMapPropertySourceForSingleConfigMap(env, s)));
			}

			addPropertySourcesFromPaths(environment, composite);

			return composite;
		}
		return null;
	}

	private MapPropertySource getMapPropertySourceForSingleConfigMap(
			ConfigurableEnvironment environment, NormalizedSource normalizedSource) {

		String configurationTarget = properties.getConfigurationTarget();
		return new ConfigMapPropertySource(client,
				getApplicationName(environment, normalizedSource.getName(),
						configurationTarget),
				getApplicationNamespace(client, normalizedSource.getNamespace(),
						configurationTarget),
				environment.getActiveProfiles());
	}

	private void addPropertySourcesFromPaths(Environment environment,
		CompositePropertySource composite) {
		properties
			.getPaths()
			.stream()
			.map(Paths::get)
			.peek(p -> {
				if(!Files.exists(p)) {
					LOG.warn("Configured input path: " + p + " will be ignored because it does not exist on the file system");
				}
			})
			.filter(Files::exists)
			.peek(p -> {
				if(!Files.isRegularFile(p)) {
					LOG.warn("Configured input path: " + p + " will be ignored because it is not a regular file");
				}
			})
			.filter(Files::isRegularFile)
			.forEach(p -> {
				try {
					String content = new String(Files.readAllBytes(p)).trim();
					String filename = p.getFileName().toString().toLowerCase();
					if(filename.endsWith(".properties")) {
						addPropertySourceIfNeeded(
							c -> PROPERTIES_TO_MAP.apply(KEY_VALUE_TO_PROPERTIES.apply(c)),
							content,
							filename,
							composite
						);
					}
					else if(filename.endsWith(".yml") || filename.endsWith(".yaml")) {
						addPropertySourceIfNeeded(
							c -> PROPERTIES_TO_MAP.apply(
								yamlParserGenerator(environment.getActiveProfiles()).apply(c)
							),
							content,
							filename,
							composite
						);
					}
				} catch (IOException e) {
					LOG.warn("Error reading input file", e);
				}
			});
	}

	private void addPropertySourceIfNeeded(Function<String, Map<String, String>> contentToMapFunction,
		String content, String name, CompositePropertySource composite) {

		Map<String, Object> map = new HashMap<>();
		map.putAll(contentToMapFunction.apply(content));
		if(map.isEmpty()) {
			LOG.warn("Property source: " + name + "will be ignored because no properties could be found");
		}
		else {
			composite.addFirstPropertySource(new MapPropertySource(name, map));
		}
	}
}
