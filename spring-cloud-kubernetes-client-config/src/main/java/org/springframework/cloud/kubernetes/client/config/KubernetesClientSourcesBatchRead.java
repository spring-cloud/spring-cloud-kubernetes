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
import java.util.concurrent.ConcurrentHashMap;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.stripConfigMaps;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.stripSecrets;

/**
 * A cache of ConfigMaps / Secrets per namespace. Makes sure we read only once from a
 * namespace.
 *
 * @author wind57
 */
public final class KubernetesClientSourcesBatchRead {

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

}
