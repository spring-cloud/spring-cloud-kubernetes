/*
 * Copyright (C) 2018 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.leader;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeaderKubernetesHelper {

	private static final String PROVIDER_KEY = "provider";

	private static final String PROVIDER = "spring-cloud-kubernetes";

	private static final String KIND_KEY = "kind";

	public static final String KIND = "leaders";

	private final LeaderProperties leaderProperties;

	private final KubernetesClient kubernetesClient;

	public LeaderKubernetesHelper(LeaderProperties leaderProperties, KubernetesClient kubernetesClient) {
		this.leaderProperties = leaderProperties;
		this.kubernetesClient = kubernetesClient;
	}

	public boolean podExists(String id) {
		return kubernetesClient.pods()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.withLabels(leaderProperties.getLabels())
			.list()
			.getItems()
			.stream()
			.map(Pod::getMetadata)
			.map(ObjectMeta::getName)
			.anyMatch(name -> name.equals(id));
	}

	public ConfigMap getConfigMap() {
		return kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.withName(leaderProperties.getConfigMapName())
			.get();
	}

	public void createConfigMap(Map<String, String> data) {
		ConfigMap newConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName(leaderProperties.getConfigMapName())
			.addToLabels(PROVIDER_KEY, PROVIDER)
			.addToLabels(KIND_KEY, KIND)
			.endMetadata()
			.addToData(data)
			.build();

		kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.create(newConfigMap);
	}

	public void updateConfigMapEntry(ConfigMap configMap, Map<String, String> newData) {
		ConfigMap newConfigMap = new ConfigMapBuilder(configMap)
			.addToData(newData)
			.build();

		updateConfigMap(configMap, newConfigMap);
	}

	public void removeConfigMapEntry(ConfigMap configMap, String key) {
		ConfigMap newConfigMap = new ConfigMapBuilder(configMap)
			.removeFromData(key)
			.build();

		updateConfigMap(configMap, newConfigMap);
	}

	private void updateConfigMap(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
		kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.withName(leaderProperties.getConfigMapName())
			.lockResourceVersion(oldConfigMap.getMetadata().getResourceVersion())
			.replace(newConfigMap);
	}

}
