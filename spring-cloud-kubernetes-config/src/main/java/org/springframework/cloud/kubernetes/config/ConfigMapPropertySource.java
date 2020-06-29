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

package org.springframework.cloud.kubernetes.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.throwingMerger;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.yamlParserGenerator;

/**
 * A {@link MapPropertySource} that uses Kubernetes config maps.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 * @author Michael Moudatsos
 */
public class ConfigMapPropertySource extends MapPropertySource {

	private static final Log LOG = LogFactory.getLog(ConfigMapPropertySource.class);

	private static final String APPLICATION_YML = "application.yml";

	private static final String APPLICATION_YAML = "application.yaml";

	private static final String APPLICATION_PROPERTIES = "application.properties";

	private static final String PREFIX = "configmap";

	private static final String LABEL_VERSION = "version";

	public ConfigMapPropertySource(KubernetesClient client, String name) {
		this(client, name, null, (Environment) null, false, null);
	}

	public ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
			String[] profiles, boolean enableVersioning, Map<String, String> labels) {
		this(client, name, namespace, createEnvironmentWithActiveProfiles(profiles),
				enableVersioning, labels);
	}

	private static Environment createEnvironmentWithActiveProfiles(
			String[] activeProfiles) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.setActiveProfiles(activeProfiles);
		return environment;
	}

	public ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
			Environment environment, boolean enableVersioning, Map<String, String> labels) {
		super(getName(client, name, namespace), asObjectMap(
				getData(client, name, namespace, environment, enableVersioning, labels)));
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

	private static Map<String, Object> getData(KubernetesClient client, String name,
			String namespace, Environment environment, boolean enableVersioning,
			Map<String, String> labels) {
		try {
			Map<String, Object> result = new LinkedHashMap<>();
			if (enableVersioning && !labels.containsKey(LABEL_VERSION)) {
				labels.put(LABEL_VERSION, environment.getProperty("info.app.version"));
			}
			ConfigMap map = null;
			if (!labels.isEmpty()) {
				Optional<ConfigMap> optMap = StringUtils.isEmpty(namespace)
						? client.configMaps().list().getItems().stream()
								.filter(configMap -> matchLabels(configMap, labels))
								.findFirst()
						: client.configMaps().inNamespace(namespace).list().getItems()
								.stream()
								.filter(configMap -> matchLabels(configMap, labels))
								.findFirst();
				if (optMap.isPresent()) {
					map = optMap.get();
				}
			}
			else {
				map = StringUtils.isEmpty(namespace)
						? client.configMaps().withName(name).get()
						: client.configMaps().inNamespace(namespace).withName(name).get();
			}

			if (map != null) {
				result.putAll(processAllEntries(map.getData(), environment));
			}

			if (environment != null) {
				for (String activeProfile : environment.getActiveProfiles()) {

					String mapNameWithProfile = name + "-" + activeProfile;

					ConfigMap mapWithProfile = StringUtils.isEmpty(namespace)
							? client.configMaps().withName(mapNameWithProfile).get()
							: client.configMaps().inNamespace(namespace)
									.withName(mapNameWithProfile).get();

					if (mapWithProfile != null) {
						result.putAll(
								processAllEntries(mapWithProfile.getData(), environment));
					}

				}
			}

			return result;

		}
		catch (Exception e) {
			LOG.warn("Can't read configMap with name: [" + name + "] in namespace:["
					+ namespace + "]. Ignoring.", e);
		}

		return new LinkedHashMap<>();
	}

	private static Map<String, Object> processAllEntries(Map<String, String> input,
			Environment environment) {

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

				return yamlParserGenerator(environment).andThen(PROPERTIES_TO_MAP)
						.apply(propertyValue);
			}
			else if (propertyName.endsWith(".properties")) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("The single property with name: [" + propertyName
							+ "] will be treated as a properties file");
				}

				return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP)
						.apply(propertyValue);
			}
			else {
				return defaultProcessAllEntries(input, environment);
			}
		}

		return defaultProcessAllEntries(input, environment);
	}

	private static Map<String, Object> defaultProcessAllEntries(Map<String, String> input,
			Environment environment) {

		return input.entrySet().stream()
				.map(e -> extractProperties(e.getKey(), e.getValue(), environment))
				.filter(m -> !m.isEmpty()).flatMap(m -> m.entrySet().stream())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue,
						throwingMerger(), LinkedHashMap::new));
	}

	private static Map<String, Object> extractProperties(String resourceName,
			String content, Environment environment) {

		if (resourceName.equals(APPLICATION_YAML)
				|| resourceName.equals(APPLICATION_YML)) {
			return yamlParserGenerator(environment).andThen(PROPERTIES_TO_MAP)
					.apply(content);
		}
		else if (resourceName.equals(APPLICATION_PROPERTIES)) {
			return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(content);
		}

		return new LinkedHashMap<String, Object>() {
			{
				put(resourceName, content);
			}
		};
	}

	private static Map<String, Object> asObjectMap(Map<String, Object> source) {
		return source.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
				Map.Entry::getValue, throwingMerger(), LinkedHashMap::new));
	}

	private static boolean matchLabels(ConfigMap configMap, Map<String, String> labels) {
		final Map<String, String> configMapLabels = configMap.getMetadata().getLabels();
		return labels.keySet().stream().noneMatch(labelKey -> !configMapLabels.containsKey(labelKey) ||
			!configMapLabels.get(labelKey).equals(labels.get(labelKey)));
	}

}
