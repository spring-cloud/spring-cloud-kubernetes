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

import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapLockRepository {

	static final String CONFIG_MAP_PREFIX = "lock";

	static final String HOLDER_KEY = "holder";

	static final String EXPIRATION_KEY = "expiration";

	private static final String PROVIDER_LABEL = "provider";

	private static final String PROVIDER_LABEL_VALUE = "spring-cloud-kubernetes";

	private static final String KIND_LABEL = "kind";

	private static final String KIND_LABEL_VALUE = "lock";

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapLockRepository.class);

	private KubernetesClient kubernetesClient;

	private String namespace;

	public ConfigMapLockRepository(KubernetesClient kubernetesClient, String namespace) {
		this.kubernetesClient = kubernetesClient;
		this.namespace = namespace;
	}

	public ConfigMap get(String name) {
		return kubernetesClient.configMaps()
			.inNamespace(namespace)
			.withName(getConfigMapName(name))
			.get();
	}

	public boolean create(String name, String holder, long expiration) {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName(getConfigMapName(name))
			.addToLabels(PROVIDER_LABEL, PROVIDER_LABEL_VALUE)
			.addToLabels(KIND_LABEL, KIND_LABEL_VALUE)
			.endMetadata()
			.addToData(HOLDER_KEY, holder)
			.addToData(EXPIRATION_KEY, String.valueOf(expiration))
			// TODO add information about the creator
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

	public void deleteAll() {
		kubernetesClient.configMaps()
			.inNamespace(namespace)
			.withLabel(PROVIDER_LABEL, PROVIDER_LABEL_VALUE)
			.withLabel(KIND_LABEL, KIND_LABEL_VALUE)
			.delete();
	}

	public void deleteExpired() {
		long now = System.currentTimeMillis();
		// TODO check that it was created by this process
		List<ConfigMap> configMaps = kubernetesClient.configMaps()
			.inNamespace(namespace)
			.withLabel(PROVIDER_LABEL, PROVIDER_LABEL_VALUE)
			.withLabel(KIND_LABEL, KIND_LABEL_VALUE)
			.list()
			.getItems()
			.stream()
			.filter(c -> Long.valueOf(c.getData().get("expiration")) < now)
			.collect(Collectors.toList());

		kubernetesClient.configMaps()
			.inNamespace(namespace)
			.delete(configMaps);
	}

	private String getConfigMapName(String name) {
		return String.format("%s-%s", CONFIG_MAP_PREFIX, name);
	}

}
