/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import static java.util.Arrays.asList;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.ABSTAIN;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.FOUND;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.NOT_FOUND;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.YamlProcessor.DocumentMatcher;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

public class ConfigMapPropertySource extends KubernetesPropertySource {
    private static final Log LOG = LogFactory.getLog(ConfigMapPropertySource.class);

    private static final String APPLICATION_YML = "application.yml";
    private static final String APPLICATION_YAML = "application.yaml";
    private static final String APPLICATION_PROPERTIES = "application.properties";

    private static final String PREFIX = "configmap";

	public ConfigMapPropertySource(KubernetesClient client, String name, ConfigMapConfigProperties config) {
		this(client, name, null, config);
	}

    public ConfigMapPropertySource(KubernetesClient client, String name, String[] profiles, ConfigMapConfigProperties config) {
        this(client, name, null, profiles, config);
    }

    public ConfigMapPropertySource(KubernetesClient client, String name, String namespace, String[] profiles, ConfigMapConfigProperties config) {
        super(getName(client, name, namespace), asObjectMap(getData(client, name, namespace, profiles, config)));
    }

    private static String getName(KubernetesClient client, String name, String namespace) {
        return new StringBuilder()
            .append(PREFIX)
            .append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR)
            .append(name)
            .append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR)
            .append(namespace == null || namespace.isEmpty() ? client.getNamespace() : namespace)
            .toString();
    }

    private static Map<String, String> getData(KubernetesClient client, String name, String namespace,
											   String[] profiles, ConfigMapConfigProperties config) {
        Map<String, String> result = new HashMap<>();
		if (config.isEnableApi()) {
			try {
				ConfigMap map = StringUtils.isEmpty(namespace)
					? client.configMaps().withName(name).get()
					: client.configMaps().inNamespace(namespace).withName(name).get();

				if (map != null) {
					result.putAll(processAllEntries(map.getData(), profiles));
				}
			} catch (Exception e) {
				LOG.warn("Can't read configMap with name: [" + name + "] in namespace:[" + namespace + "]. Ignoring", e);
			}
		}

		Map<String, String> configsFromPaths = new HashMap<>();
		putPathConfig(configsFromPaths, config.getPaths());
		result.putAll(processAllEntries(configsFromPaths, profiles));
		return result;
    }

	private static Map<String, String> processAllEntries(Map<String, String> input,
		String[] profiles) {

		Set<Entry<String, String>> entrySet = input.entrySet();
		if(entrySet.size() == 1) {
			Entry<String, String> singleEntry = entrySet.iterator().next();
			String propertyName = singleEntry.getKey();
			String propertyValue = singleEntry.getValue();
			if (propertyName.endsWith(".yml") || propertyName.endsWith(".yaml")) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("The single property with name: [" + propertyName + "] will be treated as a yaml file");
				}

				return yamlParserGenerator(profiles).andThen(PROPERTIES_TO_MAP).apply(propertyValue);
			} else if (propertyName.endsWith(".properties")) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("The single property with name: [" + propertyName + "] will be treated as a properties file");
				}

				return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(propertyValue);
			} else {
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
			  .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	private static Map<String, String> extractProperties(String resourceName, String content, String[] profiles) {
		Map<String, String> result = new HashMap<>();

    	if (resourceName.equals(APPLICATION_YAML) || resourceName.equals(APPLICATION_YML)) {
			result.putAll(yamlParserGenerator(profiles).andThen(PROPERTIES_TO_MAP).apply(content));
		} else if (resourceName.equals(APPLICATION_PROPERTIES)) {
			result.putAll(KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(content));
		} else {
			result.put(resourceName, content);
		}
			return result;
		}

    private static Map<String, Object> asObjectMap(Map<String, String> source) {
        return source.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

	private static Function<String, Properties> yamlParserGenerator(final String[] profiles) {
		return s -> {
			YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
			yamlFactory.setDocumentMatchers(properties -> {
				String profileProperty = properties.getProperty("spring.profiles");
				if (profileProperty != null && profileProperty.length() > 0) {
					return asList(profiles).contains(profileProperty) ? FOUND : NOT_FOUND;
				} else {
					return ABSTAIN;
				}
			});
			yamlFactory.setResources(new ByteArrayResource(s.getBytes()));
			return yamlFactory.getObject();
		};
	}

    private static final Function<String, Properties> KEY_VALUE_TO_PROPERTIES = s -> {
        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(s.getBytes()));
            return properties;
        } catch (IOException e) {
            throw new IllegalArgumentException();
        }
    };

    private static final Function<Properties, Map<String,String>> PROPERTIES_TO_MAP = p -> p.entrySet().stream()
            .collect(Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    e -> String.valueOf(e.getValue())));


}
