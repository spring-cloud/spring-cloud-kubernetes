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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.config.PropertySourceUtils.yamlParserGenerator;

/**
 * A {@link MapPropertySource} that uses Kubernetes config maps.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 */
public class ConfigMapPropertySource extends MapPropertySource {

	private static final Log LOG = LogFactory.getLog(ConfigMapPropertySource.class);

	private static final String APPLICATION_YML = "application.yml";

	private static final String APPLICATION_YAML = "application.yaml";

	private static final String APPLICATION_PROPERTIES = "application.properties";

	private static final String PREFIX = "configmap";

	public ConfigMapPropertySource(KubernetesClient client, String name) {
		this(client, name, null, null, (Environment) null);
	}

	public ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
			RetryPolicy retryPolicy, String[] profiles) {
		this(client, name, namespace, retryPolicy,
				createEnvironmentWithActiveProfiles(profiles));
	}

	private static Environment createEnvironmentWithActiveProfiles(
			String[] activeProfiles) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.setActiveProfiles(activeProfiles);
		return environment;
	}

	public ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
			RetryPolicy retryPolicy, Environment environment) {
		super(getName(client, name, namespace),
				asObjectMap(getData(client, name, namespace, retryPolicy, environment)));
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
			String namespace, RetryPolicy retryPolicy, Environment environment) {
		try {
			Map<String, String> result = new HashMap<>();

			ConfigMap map = tryRecoverConfigMap(client, name, namespace, retryPolicy);

			if (map != null) {
				result.putAll(processAllEntries(map.getData(), environment));
			}

			if (environment != null) {
				for (String activeProfile : environment.getActiveProfiles()) {

					String mapNameWithProfile = name + "-" + activeProfile;

					ConfigMap mapWithProfile = tryRecoverConfigMap(client,
							mapNameWithProfile, namespace, retryPolicy);

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

		return new HashMap<>();
	}

	private static Map<String, String> processAllEntries(Map<String, String> input,
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

	private static Map<String, String> defaultProcessAllEntries(Map<String, String> input,
			Environment environment) {

		return input.entrySet().stream()
				.map(e -> extractProperties(e.getKey(), e.getValue(), environment))
				.filter(m -> !m.isEmpty()).flatMap(m -> m.entrySet().stream())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

	private static Map<String, String> extractProperties(String resourceName,
			String content, Environment environment) {

		if (resourceName.equals(APPLICATION_YAML)
				|| resourceName.equals(APPLICATION_YML)) {
			return yamlParserGenerator(environment).andThen(PROPERTIES_TO_MAP)
					.apply(content);
		}
		else if (resourceName.equals(APPLICATION_PROPERTIES)) {
			return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(content);
		}

		return new HashMap<String, String>() {
			{
				put(resourceName, content);
			}
		};
	}

	private static Map<String, Object> asObjectMap(Map<String, String> source) {
		return source.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private static ConfigMap tryRecoverConfigMap(KubernetesClient client, String name,
			String namespace, RetryPolicy retryPolicy) throws InterruptedException {

		ConfigMap map = new ConfigMap();
		RetryPolicy normRetryPolicy = normalizeRetryPolicy(retryPolicy);
		int attempt = 0;
		boolean isRequestSuccessful = false;

		do {
			try {
				map = StringUtils.isEmpty(namespace)
						? client.configMaps().withName(name).get()
						: client.configMaps().inNamespace(namespace).withName(name).get();
				isRequestSuccessful = true;
			}
			catch (KubernetesClientException kce) {
				String configMapMessage = "configMap with name: [" + name
						+ "] in namespace:[" + namespace + "]";
				LOG.warn(
						"Can't read " + configMapMessage + ". Try " + (attempt + 1)
								+ " of " + normRetryPolicy.getMaxAttempts() + " ...",
						kce);

				if (attempt == normRetryPolicy.getMaxAttempts() - 1) {
					LOG.warn("Reached the maximum number of attempts to read" + " "
							+ configMapMessage);
					throw kce;
				}
				Thread.sleep(normRetryPolicy.getDelay());
			}
			attempt++;

		}
		while (!isRequestSuccessful && attempt < normRetryPolicy.getMaxAttempts());

		return map;
	}

	private static RetryPolicy normalizeRetryPolicy(RetryPolicy retryPolicy) {

		LOG.debug("Your current retry policy is: " + retryPolicy);

		RetryPolicy retryPolicyFromOption = Optional.ofNullable(retryPolicy)
				.orElseGet(RetryPolicy::new);

		int maxAttempts = retryPolicyFromOption.getMaxAttempts() == 0 ? 1
				: retryPolicyFromOption.getMaxAttempts();

		long delay = retryPolicyFromOption.getDelay() < 0 ? 0
				: retryPolicyFromOption.getDelay();

		RetryPolicy normalizeRetryPolicy = new RetryPolicy(maxAttempts, delay);
		LOG.debug("Normalized retry policy: " + normalizeRetryPolicy);
		return normalizeRetryPolicy;
	}

}
