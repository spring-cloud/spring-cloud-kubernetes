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

import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.yamlParserGenerator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class ConfigMapPropertySource extends MapPropertySource {
	private static final Log LOG = LogFactory.getLog(ConfigMapPropertySource.class);

	private static final String APPLICATION_YML = "application.yml";
	private static final String APPLICATION_YAML = "application.yaml";
	private static final String APPLICATION_PROPERTIES = "application.properties";

	private static final String PREFIX = "configmap";

	public ConfigMapPropertySource(KubernetesClient client, String name) {
		this(client, name, null, null);
	}

	public ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
		String[] profiles) {
		super(getName(client, name, namespace),
				asObjectMap(getData(client, name, namespace, profiles)));
	}

	private static String getName(KubernetesClient client, String name,
			String namespace) {
		return new StringBuilder().append(PREFIX)
				.append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR).append(name)
				.append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR)
				.append(namespace == null || namespace.isEmpty() ? client.getNamespace()
						: namespace)
				.toString();
	}

	private static Map<String, String> getData(KubernetesClient client, String name,
		String namespace, String[] profiles) {
		try {
			ConfigMap map = StringUtils.isEmpty(namespace)
				? client.configMaps().withName(name).get()
				: client.configMaps().inNamespace(namespace).withName(name).get();

			if (map != null) {
				return processAllEntries(map.getData(), profiles);
			}
		}
		catch (Exception e) {
			LOG.warn("Can't read configMap with name: [" + name + "] in namespace:["
				+ namespace + "]. Ignoring", e);
		}

		return new HashMap<>();
	}

	private static Map<String, String> processAllEntries(Map<String, String> input,
			String[] profiles) {

		Set<Entry<String, String>> entrySet = input.entrySet();
		if (entrySet.size() == 1) {
			// we handle the case where the configmap contains a single "file"
			// in this case we don't care what the name of t he file is
			Entry<String, String> singleEntry = entrySet.iterator().next();
			String propertyName = singleEntry.getKey();
			String propertyValue = singleEntry.getValue();
			if (propertyName.endsWith(".yml") || propertyName.endsWith(".yaml")) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("The single property with name: [" + propertyName
							+ "] will be treated as a yaml file");
				}

				return yamlParserGenerator(profiles).andThen(
					PROPERTIES_TO_MAP)
						.apply(propertyValue);
			}
			else if (propertyName.endsWith(".properties")) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("The single property with name: [" + propertyName
							+ "] will be treated as a properties file");
				}

				return KEY_VALUE_TO_PROPERTIES.andThen(
					PROPERTIES_TO_MAP)
						.apply(propertyValue);
			}
			else {
				return defaultProcessAllEntries(input, profiles);
			}
		}

		return defaultProcessAllEntries(input, profiles);
	}

	private static Map<String, String> defaultProcessAllEntries(Map<String, String> input,
			String[] profiles) {

		return input.entrySet().stream()
				.map(e -> extractProperties(e.getKey(), e.getValue(), profiles))
				.filter(m -> !m.isEmpty())
				.flatMap(m -> m.entrySet().stream())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

	private static Map<String, String> extractProperties(String resourceName,
			String content, String[] profiles) {

		if (resourceName.equals(APPLICATION_YAML) || resourceName.equals(APPLICATION_YML)) {
			return yamlParserGenerator(profiles).andThen(PROPERTIES_TO_MAP).apply(content);
		}
		else if (resourceName.equals(APPLICATION_PROPERTIES)) {
			return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(content);
		}

		return new HashMap<String, String>() {{
			put(resourceName, content);
		}};
	}

	private static Map<String, Object> asObjectMap(Map<String, String> source) {
		return source.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
