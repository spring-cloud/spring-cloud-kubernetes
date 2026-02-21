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

package org.springframework.cloud.kubernetes.client.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.stripConfigMaps;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.stripSecrets;

/**
 * A cache of ConfigMaps / Secrets per namespace. Makes sure we read only once from a
 * namespace.
 *
 * @author wind57
 */
public final class KubernetesClientSourcesBatchRead {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesClientSourcesBatchRead.class));

	private KubernetesClientSourcesBatchRead() {

	}

	/**
	 * at the moment our loading of config maps is using a single thread, but might change
	 * in the future, thus a thread safe structure.
	 */
	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> SECRETS_CACHE = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> CONFIGMAPS_CACHE = new ConcurrentHashMap<>();

	public static void discardSecrets() {
		SECRETS_CACHE.clear();
	}

	public static void discardConfigMaps() {
		CONFIGMAPS_CACHE.clear();
	}

	static List<StrippedSourceContainer> strippedConfigMaps(CoreV1Api coreV1Api, String namespace) {
		return CONFIGMAPS_CACHE.computeIfAbsent(namespace, x -> {
			try {
				return stripConfigMaps(coreV1Api.listNamespacedConfigMap(namespace).execute().getItems());
			}
			catch (ApiException apiException) {
				throw new RuntimeException(apiException.getResponseBody(), apiException);
			}
		});
	}

	static List<StrippedSourceContainer> strippedSecrets(CoreV1Api coreV1Api, String namespace) {
		return SECRETS_CACHE.computeIfAbsent(namespace, x -> {
			try {
				return stripSecrets(coreV1Api.listNamespacedSecret(namespace).execute().getItems());
			}
			catch (ApiException apiException) {
				throw new RuntimeException(apiException.getResponseBody(), apiException);
			}
		});
	}

	/**
	 * read secrets by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedSecrets(CoreV1Api client, String namespace,
			Map<String, String> labels) {

		List<V1Secret> secrets;
		try {
			secrets = client.listNamespacedSecret(namespace).labelSelector(labelSelector(labels)).execute().getItems();
		}
		catch (ApiException e) {
			throw new RuntimeException(e.getResponseBody(), e);
		}
		for (V1Secret secret : secrets) {
			LOG.debug(() -> "Loaded secret '" + secret.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedSecrets = stripSecrets(secrets);
		if (strippedSecrets.isEmpty()) {
			LOG.debug(() -> "No secrets in namespace '" + namespace + "'");
		}

		return strippedSecrets;
	}

	/**
	 * read configmaps by labels, without caching them.
	 */
	static List<StrippedSourceContainer> strippedConfigMaps(CoreV1Api client, String namespace,
			Map<String, String> labels) {

		List<V1ConfigMap> configMaps;
		try {
			configMaps = client.listNamespacedConfigMap(namespace)
				.labelSelector(labelSelector(labels))
				.execute()
				.getItems();
		}
		catch (ApiException e) {
			throw new RuntimeException(e.getResponseBody(), e);
		}
		for (V1ConfigMap configMap : configMaps) {
			LOG.debug(() -> "Loaded config map '" + configMap.getMetadata().getName() + "'");
		}

		List<StrippedSourceContainer> strippedConfigMaps = stripConfigMaps(configMaps);
		if (strippedConfigMaps.isEmpty()) {
			LOG.debug(() -> "No configmaps in namespace '" + namespace + "'");
		}

		return strippedConfigMaps;
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(en -> en.getKey() + "=" + en.getValue()).collect(Collectors.joining("&"));
	}

}
