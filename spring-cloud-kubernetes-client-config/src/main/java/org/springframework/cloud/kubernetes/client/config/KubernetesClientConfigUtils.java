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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.getApplicationNamespace;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesNamespaceBatched.strippedConfigMapsBatchRead;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesNamespaceBatched.strippedSecretsBatchRead;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesNonNamespaceBatched.strippedConfigMapsNonBatchRead;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesNonNamespaceBatched.strippedSecretsNonBatchRead;

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
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the config maps has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByName(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, boolean includeDefaultProfileData,
			boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (namespacedBatchRead) {
			LOG.debug("Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMapsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual configmaps in namespace : " + namespace + " with names : " + sourceNames);
			strippedConfigMaps = strippedConfigMapsNonBatchRead(client, namespace, sourceNames);
		}

		return ConfigUtils.processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false,
				includeDefaultProfileData);
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
			LinkedHashSet<String> sourceNames, Environment environment, boolean includeDefaultProfileData,
			boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedSecrets;

		if (namespacedBatchRead) {
			LOG.debug("Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecretsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with names : " + sourceNames);
			strippedSecrets = strippedSecretsNonBatchRead(client, namespace, sourceNames);
		}

		return ConfigUtils.processNamedData(strippedSecrets, environment, sourceNames, namespace, false,
				includeDefaultProfileData);
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any from (2) has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsDataByLabels(CoreV1Api client, String namespace,
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
	static MultipleSourcesContainer secretsDataByLabels(CoreV1Api client, String namespace, Map<String, String> labels,
			Environment environment, boolean namespacedBatchRead) {

		List<StrippedSourceContainer> strippedSecrets;

		if (namespacedBatchRead) {
			LOG.debug("Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecretsBatchRead(client, namespace);
		}
		else {
			LOG.debug("Will read individual secrets in namespace : " + namespace + " with labels : " + labels);
			strippedSecrets = strippedSecretsNonBatchRead(client, namespace, labels);
		}

		return ConfigUtils.processLabeledData(strippedSecrets, environment, labels, namespace, false);
	}

}
