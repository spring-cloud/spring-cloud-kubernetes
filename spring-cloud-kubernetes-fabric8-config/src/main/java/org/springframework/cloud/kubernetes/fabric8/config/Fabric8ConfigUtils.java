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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonBatched.configMapsByLabels;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonBatched.configMapsByName;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonBatched.secretsByLabels;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonBatched.secretsByName;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapsNamespaceBatched.configMapsByNamespace;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsNamespaceBatched.secretsByNamespace;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class Fabric8ConfigUtils {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigUtils.class);

	private Fabric8ConfigUtils() {
	}

	/**
	 * finds namespaces to be used for the event based reloading.
	 */
	public static Set<String> namespaces(KubernetesClient client, KubernetesNamespaceProvider provider,
			ConfigReloadProperties properties, String target) {
		Set<String> namespaces = properties.namespaces();
		if (namespaces.isEmpty()) {
			namespaces = Set.of(Fabric8Utils.getApplicationNamespace(client, null, target, provider));
		}
		LOG.debug("informer namespaces : " + namespaces);
		return namespaces;
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
	static MultipleSourcesContainer secretsDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, Set<String> profiles, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedSecrets;

		if (namespacedBatchRead) {
			LOG.debug("Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecretsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with labels : "
				+ labels);
			strippedSecrets = strippedSecretsByLabels(client, namespace, labels);
		}

		return ConfigUtils.processLabeledData(strippedSecrets, environment, labels, namespace, profiles, true);
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
	static MultipleSourcesContainer configMapsDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, Set<String> profiles, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (namespacedBatchRead) {
			LOG.debug("Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMapsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual configmaps in namespace : " + namespace + " with labels : "
				+ labels);
			strippedConfigMaps = strippedConfigMapsByLabels(client, namespace, labels);
		}

		if (strippedConfigMaps.isEmpty()) {
			return MultipleSourcesContainer.empty();
		}

		return ConfigUtils.processLabeledData(strippedConfigMaps, environment, labels, namespace, profiles, false);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the secrets has a single yaml/properties file
	 *     4. gather all the names of the secrets + decoded data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsDataByName(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedSecrets;

		if (namespacedBatchRead) {
			LOG.debug("Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecretsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with names : "
				+ sourceNames);
			strippedSecrets = strippedSecretsByName(client, namespace, sourceNames);
		}

		if (strippedSecrets.isEmpty()) {
			return MultipleSourcesContainer.empty();
		}
		return ConfigUtils.processNamedData(strippedSecrets, environment, sourceNames, namespace, true);
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the config maps has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByName(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (namespacedBatchRead) {
			LOG.debug("Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMapsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual configmaps in namespace : " + namespace + " with names : "
				+ sourceNames);
			strippedConfigMaps = strippedConfigMapsByName(client, namespace, sourceNames);
		}

		if (strippedConfigMaps.isEmpty()) {
			return MultipleSourcesContainer.empty();
		}
		return ConfigUtils.processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false);
	}

	static List<StrippedSourceContainer> strippedConfigMaps(List<ConfigMap> configMaps) {
		return configMaps.stream()
			.map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
				configMap.getMetadata().getName(), configMap.getData()))
			.toList();
	}

	static List<StrippedSourceContainer> strippedSecrets(List<Secret> secrets) {
		return secrets.stream()
			.map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(), secret.getMetadata().getName(),
				secret.getData()))
			.toList();
	}

	/**
	 * read specific configmaps (by name) in the given namespaces, and strip them down
	 * to only needed fields.
	 */
	private static List<StrippedSourceContainer> strippedConfigMapsByName(
		KubernetesClient client, String namespace, LinkedHashSet<String> sourceNames) {
		List<StrippedSourceContainer> strippedConfigMaps = configMapsByName(client, namespace, sourceNames);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read specific secrets (by name) in the given namespaces, and strip them down
	 * to only needed fields.
	 */
	private static List<StrippedSourceContainer> strippedSecretsByName(
		KubernetesClient client, String namespace, LinkedHashSet<String> sourceNames) {
		List<StrippedSourceContainer> strippedSecrets = secretsByName(client, namespace, sourceNames);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	/**
	 * read specific configmaps (by labels) in the given namespaces, and strip them down
	 * to only needed fields.
	 */
	private static List<StrippedSourceContainer> strippedConfigMapsByLabels(
		KubernetesClient client, String namespace, Map<String, String> labels) {
		List<StrippedSourceContainer> strippedConfigMaps = configMapsByLabels(client, namespace, labels);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read specific configmaps (by labels) in the given namespaces, and strip them down
	 * to only needed fields.
	 */
	private static List<StrippedSourceContainer> strippedSecretsByLabels(
		KubernetesClient client, String namespace, Map<String, String> labels) {
		List<StrippedSourceContainer> strippedSecrets = secretsByLabels(client, namespace, labels);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	/**
	 * read all configmaps in the given namespace (batch), and strip them down
	 * to only needed fields.
	 */
	private static List<StrippedSourceContainer> strippedConfigMapsBatchRead(KubernetesClient client, String namespace) {
		List<StrippedSourceContainer> strippedConfigMaps = configMapsByNamespace(client, namespace);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	private static List<StrippedSourceContainer> strippedSecretsBatchRead(KubernetesClient client, String namespace) {
		List<StrippedSourceContainer> strippedSecrets = secretsByNamespace(client, namespace);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

}
