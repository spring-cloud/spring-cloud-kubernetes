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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
final class Fabric8ConfigUtils {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigUtils.class);

	private static final Map.Entry<Set<String>, Map<String, Object>> EMPTY = Map.entry(Set.of(), Map.of());

	private Fabric8ConfigUtils() {
	}

	/**
	 * this method does the namespace resolution for both config map and secrets
	 * implementations. It tries these places to find the namespace:
	 *
	 * <pre>
	 *     1. from a normalized source (which can be null)
	 *     2. from a property 'spring.cloud.kubernetes.client.namespace', if such is present
	 *     3. from a String residing in a file denoted by `spring.cloud.kubernetes.client.serviceAccountNamespacePath`
	 * 	      property, if such is present
	 * 	   4. from a String residing in `/var/run/secrets/kubernetes.io/serviceaccount/namespace` file,
	 * 	  	  if such is present (kubernetes default path)
	 * 	   5. from KubernetesClient::getNamespace, which is implementation specific.
	 * </pre>
	 *
	 * If any of the above fail, we throw a NamespaceResolutionFailedException.
	 * @param namespace normalized namespace
	 * @param configurationTarget Config Map/Secret
	 * @param provider the provider which computes the namespace
	 * @param client fabric8 Kubernetes client
	 * @return application namespace
	 * @throws NamespaceResolutionFailedException when namespace could not be resolved
	 */
	static String getApplicationNamespace(KubernetesClient client, String namespace, String configurationTarget,
			KubernetesNamespaceProvider provider) {

		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace from normalized source : " + namespace);
			return namespace;
		}

		if (provider != null) {
			String providerNamespace = provider.getNamespace();
			if (StringUtils.hasText(providerNamespace)) {
				LOG.debug(configurationTarget + " namespace from provider : " + namespace);
				return providerNamespace;
			}
		}

		String clientNamespace = client.getNamespace();
		LOG.debug(configurationTarget + " namespace from client : " + clientNamespace);
		if (clientNamespace == null) {
			throw new NamespaceResolutionFailedException("unresolved namespace");
		}
		return clientNamespace;

	}

	static Map.Entry<Set<String>, Map<String, Object>> secretsDataByName(KubernetesClient client, String namespace,
			Set<String> sourceNames, Environment environment,
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		LOG.debug("Reading all secrets in namespace '" + namespace + "'");
		SecretList secretList = client.secrets().inNamespace(namespace).list();
		if (secretList == null || secretList.getItems() == null || secretList.getItems().isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
			return EMPTY;
		}

		Set<String> secretNames = new HashSet<>();
		Map<String, Object> result = new HashMap<>();

		secretList.getItems().stream().filter(secret -> sourceNames.contains(secret.getMetadata().getName()))
				.collect(Collectors.toList()).forEach(foundSecret -> {
					String foundSecretName = foundSecret.getMetadata().getName();
					LOG.debug("Loaded secret with name : '" + foundSecretName + " in namespace: '" + namespace + "'");
					secretNames.add(foundSecretName);

					Map<String, String> decoded = decodeData(foundSecret.getData());
					result.putAll(entriesProcessor.apply(decoded, environment));
				});

		return Map.entry(secretNames, result);

	}

	/**
	 * returns the secret names and the values they hold.
	 */
	static Map.Entry<Set<String>, Map<String, Object>> secretsDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment,
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		LOG.debug("Loading Secret with labels '" + labels + "' in namespace '" + namespace + "'");
		List<Secret> secrets = client.secrets().inNamespace(namespace).withLabels(labels).list().getItems();

		if (secrets == null || secrets.isEmpty()) {
			LOG.warn("secret(s) with labels : '" + labels + "' not present in namespace : '" + namespace + "'");
			return Map.entry(Set.of(), Collections.emptyMap());
		}

		Set<String> secretNames = new HashSet<>();
		Map<String, Object> result = new HashMap<>();

		secrets.forEach(foundSecret -> {
			String foundSecretName = foundSecret.getMetadata().getName();
			LOG.debug("Loaded secret with name : '" + foundSecretName + " in namespace: '" + namespace + "'");
			secretNames.add(foundSecretName);

			Map<String, String> decoded = decodeData(foundSecret.getData());
			result.putAll(entriesProcessor.apply(decoded, environment));
		});

		return Map.entry(secretNames, result);
	}

	static Map.Entry<Set<String>, Map<String, Object>> configMapsDataByName(KubernetesClient client, String namespace,
			Set<String> sourceNames, Environment environment,
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		LOG.debug("Reading all configmaps in namespace '" + namespace + "'");
		ConfigMapList configMapList = client.configMaps().inNamespace(namespace).list();
		if (configMapList == null || configMapList.getItems() == null || configMapList.getItems().isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
			return EMPTY;
		}

		Set<String> configMapNames = new HashSet<>();
		Map<String, Object> result = new HashMap<>();

		configMapList.getItems().stream().filter(configMap -> sourceNames.contains(configMap.getMetadata().getName()))
				.collect(Collectors.toList()).forEach(foundConfigMap -> {
					String foundConfigMapName = foundConfigMap.getMetadata().getName();
					LOG.debug("Loaded configmap with name : '" + foundConfigMapName + " in namespace: '" + namespace
							+ "'");
					configMapNames.add(foundConfigMapName);

					result.putAll(entriesProcessor.apply(foundConfigMap.getData(), environment));
				});

		return Map.entry(configMapNames, result);

	}

	private static Map<String, String> decodeData(Map<String, String> data) {
		Map<String, String> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((key, value) -> result.put(key, new String(Base64.getDecoder().decode(value)).trim()));
		return result;
	}

}
