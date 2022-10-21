/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientConfigUtils {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigUtils.class);

	// k8s-native client already returns data from secrets as being decoded
	// this flags makes sure we use it everywhere
	private static final boolean DECODE = Boolean.FALSE;

	private KubernetesClientConfigUtils() {
	}

	/**
	 * finds namespaces to be used for the event based reloading.
	 */
	public static Set<String> namespaces(KubernetesNamespaceProvider provider, ConfigReloadProperties properties,
			String target) {
		Set<String> namespaces = properties.namespaces();
		if (namespaces.isEmpty()) {
			namespaces = Set.of(getApplicationNamespace(null, target, provider));
		}
		LOG.debug("informer namespaces : " + namespaces);
		return namespaces;
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
	 * </pre>
	 *
	 * If any of the above fail, we throw a NamespaceResolutionFailedException.
	 * @param namespace normalized namespace
	 * @param configurationTarget Config Map/Secret
	 * @param provider the provider which computes the namespace
	 * @return application namespace
	 * @throws NamespaceResolutionFailedException when namespace could not be resolved
	 */
	static String getApplicationNamespace(String namespace, String configurationTarget,
			KubernetesNamespaceProvider provider) {
		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace : " + namespace);
			return namespace;
		}

		if (provider != null) {
			String providerNamespace = provider.getNamespace();
			if (StringUtils.hasText(providerNamespace)) {
				LOG.debug(configurationTarget + " namespace from provider : " + namespace);
				return providerNamespace;
			}
		}

		throw new NamespaceResolutionFailedException("unresolved namespace");
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. with secret names from (2), find out if there are any profile based secrets (if profiles is not empty)
	 *     4. concat (2) and (3) and these are the secrets we are interested in
	 *     5. see if any of the secrets from (4) has a single yaml/properties file
	 *     6. gather all the names of the secrets (from 4) + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsDataByLabels(CoreV1Api client, String namespace, Map<String, String> labels,
			Environment environment, Set<String> profiles) {
		List<V1Secret> secrets = secretsSearch(client, namespace);
		if (ConfigUtils.noSources(secrets, namespace)) {
			return MultipleSourcesContainer.empty();
		}

		List<StrippedSourceContainer> strippedSources = strippedSecrets(secrets);
		return ConfigUtils.processLabeledData(strippedSources, environment, labels, namespace, profiles, DECODE);

	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. with config maps names from (2), find out if there are any profile based ones (if profiles is not empty)
	 *     4. concat (2) and (3) and these are the config maps we are interested in
	 *     5. see if any from (4) has a single yaml/properties file
	 *     6. gather all the names of the config maps (from 4) + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByLabels(CoreV1Api client, String namespace,
			Map<String, String> labels, Environment environment, Set<String> profiles) {

		List<V1ConfigMap> configMaps = configMapsSearch(client, namespace);
		if (ConfigUtils.noSources(configMaps, namespace)) {
			return MultipleSourcesContainer.empty();
		}

		List<StrippedSourceContainer> strippedSources = strippedConfigMaps(configMaps);
		return ConfigUtils.processLabeledData(strippedSources, environment, labels, namespace, profiles, DECODE);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the secrets has a single yaml/properties file
	 *     4. gather all the names of the secrets + decoded data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsDataByName(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment) {
		List<V1Secret> secrets = secretsSearch(client, namespace);
		if (ConfigUtils.noSources(secrets, namespace)) {
			return MultipleSourcesContainer.empty();
		}

		List<StrippedSourceContainer> strippedSources = strippedSecrets(secrets);
		return ConfigUtils.processNamedData(strippedSources, environment, sourceNames, namespace, DECODE);

	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the config maps has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByName(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment) {
		List<V1ConfigMap> configMaps = configMapsSearch(client, namespace);
		if (ConfigUtils.noSources(configMaps, namespace)) {
			return MultipleSourcesContainer.empty();
		}

		List<StrippedSourceContainer> strippedSources = strippedConfigMaps(configMaps);
		return ConfigUtils.processNamedData(strippedSources, environment, sourceNames, namespace, DECODE);

	}

	private static List<V1Secret> secretsSearch(CoreV1Api client, String namespace) {
		LOG.debug("Loading all secrets in namespace '" + namespace + "'");
		try {
			return client.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems();
		}
		catch (ApiException apiException) {
			throw new RuntimeException(apiException.getResponseBody(), apiException);
		}
	}

	private static List<V1ConfigMap> configMapsSearch(CoreV1Api client, String namespace) {
		LOG.debug("Loading all config maps in namespace '" + namespace + "'");
		try {
			return client.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems();
		}
		catch (ApiException apiException) {
			throw new RuntimeException(apiException.getResponseBody(), apiException);
		}
	}

	private static List<StrippedSourceContainer> strippedSecrets(List<V1Secret> secrets) {
		return secrets.stream().map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(),
				secret.getMetadata().getName(), transform(secret.getData()))).collect(Collectors.toList());
	}

	private static List<StrippedSourceContainer> strippedConfigMaps(List<V1ConfigMap> configMaps) {
		return configMaps.stream().map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
				configMap.getMetadata().getName(), configMap.getData())).collect(Collectors.toList());
	}

	private static Map<String, String> transform(Map<String, byte[]> in) {
		return in.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, en -> new String(en.getValue())));
	}

}
