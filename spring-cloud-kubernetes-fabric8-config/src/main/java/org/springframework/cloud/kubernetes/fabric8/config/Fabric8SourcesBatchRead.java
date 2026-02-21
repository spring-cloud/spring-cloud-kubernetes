/*
 * Copyright 2013-present the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.stripConfigMaps;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.stripSecrets;

/**
 * A cache of ConfigMaps / Secrets per namespace. Makes sure we read only once from a
 * namespace.
 *
 * @author wind57
 */
final class Fabric8SourcesBatchRead {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8SourcesBatchRead.class));

	private Fabric8SourcesBatchRead() {

	}

	/**
	 * at the moment, our loading of config maps is using a single thread, but might
	 * change in the future, thus a thread safe structure.
	 */
	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> SECRETS_CACHE = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> CONFIGMAPS_CACHE = new ConcurrentHashMap<>();

	static void discardSecrets() {
		SECRETS_CACHE.clear();
	}

	static void discardConfigMaps() {
		CONFIGMAPS_CACHE.clear();
	}

	static List<StrippedSourceContainer> strippedConfigMaps(KubernetesClient client, String namespace) {
		return CONFIGMAPS_CACHE.computeIfAbsent(namespace,
				x -> stripConfigMaps(client.configMaps().inNamespace(namespace).list().getItems()));
	}

	static List<StrippedSourceContainer> strippedSecrets(KubernetesClient client, String namespace) {
		return SECRETS_CACHE.computeIfAbsent(namespace,
				x -> stripSecrets(client.secrets().inNamespace(namespace).list().getItems()));
	}

	/**
	 * read configmaps by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMaps(KubernetesClient client, String namespace,
		Map<String, String> labels) {

		List<ConfigMap> configMaps = client.configMaps().inNamespace(namespace).withLabels(labels).list().getItems();
		for (ConfigMap configMap : configMaps) {
			LOG.debug(() -> "Loaded config map '" + configMap.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedConfigMaps = stripConfigMaps(configMaps);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug(() -> "No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read secrets by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecrets(KubernetesClient client, String namespace,
		Map<String, String> labels) {

		List<Secret> secrets = client.secrets().inNamespace(namespace).withLabels(labels).list().getItems();
		for (Secret secret : secrets) {
			LOG.debug(() -> "Loaded secret '" + secret.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedSecrets = stripSecrets(secrets);
		if (strippedSecrets.isEmpty()) {
			LOG.debug(() -> "No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

}
