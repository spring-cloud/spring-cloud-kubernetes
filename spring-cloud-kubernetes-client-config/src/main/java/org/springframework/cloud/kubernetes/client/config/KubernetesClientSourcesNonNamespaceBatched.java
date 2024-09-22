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

package org.springframework.cloud.kubernetes.client.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

final class KubernetesClientSourcesNonNamespaceBatched {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientSourcesNonNamespaceBatched.class));

	private KubernetesClientSourcesNonNamespaceBatched() {

	}

	/**
	 * read configmaps by name, one by one, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMapsNonBatchRead(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames) {

		List<V1ConfigMap> configMaps = new ArrayList<>(sourceNames.size());

		for (String sourceName : sourceNames) {
			V1ConfigMap configMap;
			try {
				configMap = client.readNamespacedConfigMap(namespace, sourceName, null);
			}
			catch (ApiException e) {
				throw new RuntimeException(e.getResponseBody(), e);
			}
			if (configMap != null) {
				LOG.debug("Loaded config map '" + sourceName + "'");
				configMaps.add(configMap);
			}
		}

		List<StrippedSourceContainer> strippedConfigMaps = KubernetesClientSourcesStripper
			.strippedConfigMaps(configMaps);

		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read secrets by name, one by one, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecretsNonBatchRead(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames) {

		List<V1Secret> secrets = new ArrayList<>(sourceNames.size());

		for (String sourceName : sourceNames) {
			V1Secret secret;
			try {
				secret = client.readNamespacedSecret(sourceName, namespace, null);
			}
			catch (ApiException e) {
				throw new RuntimeException(e.getResponseBody(), e);
			}
			if (secret != null) {
				LOG.debug("Loaded config map '" + sourceName + "'");
				secrets.add(secret);
			}
		}

		List<StrippedSourceContainer> strippedSecrets = KubernetesClientSourcesStripper.strippedSecrets(secrets);

		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	/**
	 * read configmaps by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMapsNonBatchRead(CoreV1Api client, String namespace,
			Map<String, String> labels) {

		List<V1ConfigMap> configMaps;
		try {
			configMaps = client
				.listNamespacedConfigMap(namespace, null, null, null, null, labelSelector(labels), null, null, null,
						null, null, null)
				.getItems();
		}
		catch (ApiException e) {
			throw new RuntimeException(e.getResponseBody(), e);
		}
		for (V1ConfigMap configMap : configMaps) {
			LOG.debug("Loaded config map '" + configMap.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedConfigMaps = KubernetesClientSourcesStripper
			.strippedConfigMaps(configMaps);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug("No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	/**
	 * read secrets by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecretsNonBatchRead(CoreV1Api client, String namespace,
			Map<String, String> labels) {

		List<V1Secret> secrets;
		try {
			secrets = client
				.listNamespacedSecret(namespace, null, null, null, null, labelSelector(labels), null, null, null, null,
						null, null)
				.getItems();
		}
		catch (ApiException e) {
			throw new RuntimeException(e.getResponseBody(), e);
		}
		for (V1Secret secret : secrets) {
			LOG.debug("Loaded secret '" + secret.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedSecrets = KubernetesClientSourcesStripper.strippedSecrets(secrets);
		if (strippedSecrets.isEmpty()) {
			LOG.debug("No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(en -> en.getKey() + "=" + en.getValue()).collect(Collectors.joining("&"));
	}

}
