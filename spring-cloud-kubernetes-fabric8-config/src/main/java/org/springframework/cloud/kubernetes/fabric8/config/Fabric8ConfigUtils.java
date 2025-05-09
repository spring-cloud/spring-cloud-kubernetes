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

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNamespaceBatched.strippedConfigMapsBatchRead;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNamespaceBatched.strippedSecretsBatchRead;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonNamespaceBatched.strippedConfigMapsNonBatchRead;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesNonNamespaceBatched.strippedSecretsNonBatchRead;

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
			LOG.debug("Will read individual configmaps in namespace : " + namespace + " with names : " + sourceNames);
			strippedConfigMaps = strippedConfigMapsNonBatchRead(client, namespace, sourceNames);
		}

		return ConfigUtils.processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false);
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
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with names : " + sourceNames);
			strippedSecrets = strippedSecretsNonBatchRead(client, namespace, sourceNames);
		}

		return ConfigUtils.processNamedData(strippedSecrets, environment, sourceNames, namespace, true);
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any from (2) has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (namespacedBatchRead) {
			LOG.debug("Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMapsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual configmaps in namespace : " + namespace + " with labels : " + labels);
			strippedConfigMaps = strippedConfigMapsNonBatchRead(client, namespace, labels);
		}

		return ConfigUtils.processLabeledData(strippedConfigMaps, environment, labels, namespace, false);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any of the secrets from (2) has a single yaml/properties file
	 *     4. gather all the names of the secrets + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedSecrets;

		if (namespacedBatchRead) {
			LOG.debug("Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecretsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with labels : " + labels);
			strippedSecrets = strippedSecretsNonBatchRead(client, namespace, labels);
		}

		return ConfigUtils.processLabeledData(strippedSecrets, environment, labels, namespace, true);
	}

}
