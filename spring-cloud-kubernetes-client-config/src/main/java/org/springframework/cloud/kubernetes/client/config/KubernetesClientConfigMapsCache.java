/*
 * Copyright 2013-2022 the original author or authors.
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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapCache;
import org.springframework.core.log.LogAccessor;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache of V1ConfigMap(s) per namespace. Makes sure we read config maps only once
 * from a namespace.
 *
 * @author wind57
 */
final class KubernetesClientConfigMapsCache implements ConfigMapCache {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesClientConfigMapsCache.class));

	/**
	 * at the moment our loading of config maps is using a single thread, but might change
	 * in the future, thus a thread safe structure.
	 */
	private static final ConcurrentHashMap<String, List<V1ConfigMap>> CACHE = new ConcurrentHashMap<>();

	static List<V1ConfigMap> byNamespace(CoreV1Api coreV1Api, String namespace) {
		boolean [] b = new boolean[1];
		List<V1ConfigMap> result = CACHE.computeIfAbsent(namespace, x -> {
			try {
				b[0] = true;
				return coreV1Api.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems();
			} catch (ApiException apiException) {
				throw new RuntimeException(apiException.getResponseBody(), apiException);
			}
		});

		if (b[0]) {
			LOG.debug(() -> "Loading all config maps in namespace '" + namespace + "'");
		}
		else {
			LOG.debug(() -> "Loading (from cache) all config maps in namespace '" + namespace + "'");
		}

		return result;
	}

	@Override
	public void discardAll() {
		CACHE.clear();
	}
}
