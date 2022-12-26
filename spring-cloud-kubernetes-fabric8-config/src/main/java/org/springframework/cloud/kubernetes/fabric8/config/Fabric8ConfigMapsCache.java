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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapCache;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

/**
 * A cache of ConfigMaps per namespace. Makes sure we read config maps only once from a
 * namespace.
 *
 * @author wind57
 */
final class Fabric8ConfigMapsCache implements ConfigMapCache {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ConfigMapsCache.class));

	/**
	 * at the moment our loading of config maps is using a single thread, but might change
	 * in the future, thus a thread safe structure.
	 */
	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> CACHE = new ConcurrentHashMap<>();

	@Override
	public void discardAll() {
		CACHE.clear();
	}

	static List<StrippedSourceContainer> byNamespace(KubernetesClient client, String namespace) {
		boolean[] b = new boolean[1];
		List<StrippedSourceContainer> result = CACHE.computeIfAbsent(namespace, x -> {
			b[0] = true;
			return strippedConfigMaps(client.configMaps().inNamespace(namespace).list().getItems());
		});

		if (b[0]) {
			LOG.debug(() -> "Loaded all config maps in namespace '" + namespace + "'");
		}
		else {
			LOG.debug(() -> "Loaded (from cache) all config maps in namespace '" + namespace + "'");
		}

		return result;
	}

	private static List<StrippedSourceContainer> strippedConfigMaps(List<ConfigMap> configMaps) {
		return configMaps.stream().map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
				configMap.getMetadata().getName(), configMap.getData())).collect(Collectors.toList());
	}

}
