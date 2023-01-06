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
			Map<String, String> labels, Environment environment, Set<String> profiles) {
		List<StrippedSourceContainer> strippedSecrets = strippedSecrets(client, namespace);
		if (strippedSecrets.isEmpty()) {
			return MultipleSourcesContainer.empty();
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
			Map<String, String> labels, Environment environment, Set<String> profiles) {
		List<StrippedSourceContainer> strippedConfigMaps = strippedConfigMaps(client, namespace);
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
			LinkedHashSet<String> sourceNames, Environment environment) {
		List<StrippedSourceContainer> strippedSecrets = strippedSecrets(client, namespace);
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
			LinkedHashSet<String> sourceNames, Environment environment) {
		List<StrippedSourceContainer> strippedConfigMaps = strippedConfigMaps(client, namespace);
		if (strippedConfigMaps.isEmpty()) {
			return MultipleSourcesContainer.empty();
		}
		return ConfigUtils.processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false);
	}

	private static List<StrippedSourceContainer> strippedConfigMaps(KubernetesClient client, String namespace) {
		List<StrippedSourceContainer> strippedConfigMaps = Fabric8ConfigMapsCache.byNamespace(client, namespace);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	private static List<StrippedSourceContainer> strippedSecrets(KubernetesClient client, String namespace) {
		List<StrippedSourceContainer> strippedSecrets = Fabric8SecretsCache.byNamespace(client, namespace);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

}
