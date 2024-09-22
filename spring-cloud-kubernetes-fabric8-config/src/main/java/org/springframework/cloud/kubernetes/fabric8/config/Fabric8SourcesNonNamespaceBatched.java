/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

/**
 * non batch reads (not reading in the whole namespace) of configmaps and secrets.
 *
 * @author wind57
 */
final class Fabric8SourcesNonNamespaceBatched {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8SourcesNonNamespaceBatched.class));

	private Fabric8SourcesNonNamespaceBatched() {

	}

	/**
	 * read configmaps by name, one by one, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMapsNonBatchRead(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames) {

		List<ConfigMap> configMaps = new ArrayList<>(sourceNames.size());

		for (String sourceName : sourceNames) {
			ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(sourceName).get();
			if (configMap != null) {
				LOG.debug("Loaded config map '" + sourceName + "'");
				configMaps.add(configMap);
			}
		}

		List<StrippedSourceContainer> strippedConfigMaps = Fabric8SourcesStripper.strippedConfigMaps(configMaps);

		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read secrets by name, one by one, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecretsNonBatchRead(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames) {

		List<Secret> secrets = new ArrayList<>(sourceNames.size());

		for (String sourceName : sourceNames) {
			Secret secret = client.secrets().inNamespace(namespace).withName(sourceName).get();
			if (secret != null) {
				LOG.debug("Loaded config map '" + sourceName + "'");
				secrets.add(secret);
			}
		}

		List<StrippedSourceContainer> strippedSecrets = Fabric8SourcesStripper.strippedSecrets(secrets);

		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	/**
	 * read configmaps by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMapsNonBatchRead(KubernetesClient client, String namespace,
			Map<String, String> labels) {

		List<ConfigMap> configMaps = client.configMaps().inNamespace(namespace).withLabels(labels).list().getItems();
		for (ConfigMap configMap : configMaps) {
			LOG.debug("Loaded config map '" + configMap.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedConfigMaps = Fabric8SourcesStripper.strippedConfigMaps(configMaps);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read secrets by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecretsNonBatchRead(KubernetesClient client, String namespace,
			Map<String, String> labels) {

		List<Secret> secrets = client.secrets().inNamespace(namespace).withLabels(labels).list().getItems();
		for (Secret secret : secrets) {
			LOG.debug("Loaded secret '" + secret.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedSecrets = Fabric8SourcesStripper.strippedSecrets(secrets);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

}
