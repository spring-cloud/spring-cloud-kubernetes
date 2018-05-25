/*
 *     Copyright (C) 2018 to the original authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.cloud.kubernetes.lock;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapLockRepository {

	static final String CONFIG_MAP_PREFIX = "lock";

	static final String HOLDER_KEY = "holder";

	static final String CREATED_AT_KEY = "created_at";

	static final String PROVIDER_LABEL = "provider";

	static final String PROVIDER_LABEL_VALUE = "spring-cloud-kubernetes";

	static final String KIND_LABEL = "kind";

	static final String KIND_LABEL_VALUE = "lock";

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapLockRepository.class);

	private KubernetesClient kubernetesClient;

	private String namespace;

	public ConfigMapLockRepository(KubernetesClient kubernetesClient, String namespace) {
		this.kubernetesClient = kubernetesClient;
		this.namespace = namespace;
	}

	public Optional<ConfigMap> get(String name) {
		String configMapName = getConfigMapName(name);
		ConfigMap configMap = kubernetesClient.configMaps()
			.inNamespace(namespace)
			.withName(configMapName)
			.get();

		return Optional.ofNullable(configMap);
	}

	public boolean create(String name, String holder, long createdAt) {
		String configMapName = getConfigMapName(name);
		String createdAtString = String.valueOf(createdAt);
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName(configMapName)
			.addToLabels(PROVIDER_LABEL, PROVIDER_LABEL_VALUE)
			.addToLabels(KIND_LABEL, KIND_LABEL_VALUE)
			.endMetadata()
			.addToData(HOLDER_KEY, holder)
			.addToData(CREATED_AT_KEY, createdAtString)
			.build();

		try {
			kubernetesClient.configMaps()
				.inNamespace(namespace)
				.create(configMap);
		} catch (KubernetesClientException e) {
			LOGGER.warn("Failed to create ConfigMap for name '{}': ", name, e.getMessage());
			return false;
		}

		return true;
	}

	public void delete(String name) {
		// TODO make sure that only creator can delete the lock
		kubernetesClient.configMaps()
			.inNamespace(namespace)
			.withName(getConfigMapName(name))
			.delete();
	}

	public void deleteIfOlderThan(String name, long age) {
		get(name)
			.filter(m -> this.isOlder(m, age))
			// TODO what if someone else deletes and creates a lock in this gap?
			.ifPresent(c -> delete(name));
	}

	private boolean isOlder(ConfigMap configMap, long age) {
		String createdAtString = configMap.getData().get(CREATED_AT_KEY);
		return System.currentTimeMillis() - Long.valueOf(createdAtString) > age;
	}

	private String getConfigMapName(String name) {
		return String.format("%s-%s", CONFIG_MAP_PREFIX, name);
	}

}
