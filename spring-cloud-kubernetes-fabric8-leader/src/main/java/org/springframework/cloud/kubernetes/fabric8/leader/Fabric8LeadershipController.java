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

package org.springframework.cloud.kubernetes.fabric8.leader;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import org.springframework.cloud.kubernetes.commons.leader.Leader;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeadershipController;
import org.springframework.cloud.kubernetes.commons.leader.PodReadinessWatcher;
import org.springframework.core.log.LogAccessor;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.guarded;

/**
 * @author Gytis Trikleris
 */
public class Fabric8LeadershipController extends LeadershipController {

	private static final LogAccessor LOGGER = new LogAccessor(Fabric8LeadershipController.class);

	private final KubernetesClient kubernetesClient;

	private final ReentrantLock lock = new ReentrantLock();

	public Fabric8LeadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher, KubernetesClient kubernetesClient) {
		super(candidate, leaderProperties, leaderEventPublisher);
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public void update() {
		guarded(lock, () -> {
			LOGGER.debug(() -> "Checking leader state");
			ConfigMap configMap = getConfigMap();
			if (configMap == null && !leaderProperties.isCreateConfigMap()) {
				LOGGER.warn("ConfigMap '" + leaderProperties.getConfigMapName()
						+ "' does not exist and leaderProperties.isCreateConfigMap() "
						+ "is false, cannot acquire leadership");
				notifyOnFailedToAcquire();
				return;
			}
			Leader leader = extractLeader(configMap);

			if (leader != null && isPodReady(leader.getId())) {
				handleLeaderChange(leader);
				return;
			}

			if (leader != null && leader.isCandidate(candidate)) {
				revoke(configMap);
			}
			else {
				acquire(configMap);
			}
		});

	}

	public void revoke() {
		guarded(lock, () -> {
			ConfigMap configMap = getConfigMap();
			Leader leader = extractLeader(configMap);

			if (leader != null && leader.isCandidate(candidate)) {
				revoke(configMap);
			}
		});
	}

	private void revoke(ConfigMap configMap) {
		LOGGER.debug(() -> "Trying to revoke leadership from :" + candidate);

		try {
			String leaderKey = getLeaderKey();
			removeConfigMapEntry(configMap, leaderKey);
			handleLeaderChange(null);
		}
		catch (KubernetesClientException e) {
			LOGGER.warn("Failure when revoking leadership for : " + candidate + "because : " + e.getMessage());
		}
	}

	private void acquire(ConfigMap configMap) {
		LOGGER.debug(() -> "Trying to acquire leadership for :" + candidate);

		if (!isPodReady(candidate.getId())) {
			LOGGER.debug("Pod : " + candidate + "is not ready at the moment, cannot acquire leadership");
			return;
		}

		try {
			Map<String, String> data = getLeaderData(candidate);

			if (configMap == null) {
				createConfigMap(data);
			}
			else {
				updateConfigMapEntry(configMap, data);
			}

			Leader newLeader = new Leader(candidate.getRole(), candidate.getId());
			handleLeaderChange(newLeader);
		}
		catch (KubernetesClientException e) {
			LOGGER.warn(() -> "Failure when acquiring leadership for : " + candidate + " because : " + e.getMessage());
			notifyOnFailedToAcquire();
		}
	}

	@Override
	protected PodReadinessWatcher createPodReadinessWatcher(String localLeaderId) {
		return new Fabric8PodReadinessWatcher(localLeaderId, kubernetesClient, this);
	}

	private Leader extractLeader(ConfigMap configMap) {
		if (configMap == null) {
			return null;
		}

		return extractLeader(configMap.getData());
	}

	private boolean isPodReady(String name) {
		return kubernetesClient.pods().withName(name).isReady();
	}

	private ConfigMap getConfigMap() {
		return kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.withName(leaderProperties.getConfigMapName())
			.get();
	}

	private void createConfigMap(Map<String, String> data) {
		LOGGER.debug(() -> "Creating new config map with data: " + data);

		ConfigMap newConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName(leaderProperties.getConfigMapName())
			.addToLabels(PROVIDER_KEY, PROVIDER)
			.addToLabels(KIND_KEY, KIND)
			.endMetadata()
			.addToData(data)
			.build();

		kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.resource(newConfigMap)
			.create();
	}

	private void updateConfigMapEntry(ConfigMap configMap, Map<String, String> newData) {
		LOGGER.debug(() -> "Adding new data to config map: " + newData);
		ConfigMap newConfigMap = new ConfigMapBuilder(configMap).addToData(newData).build();
		updateConfigMap(configMap, newConfigMap);
	}

	private void removeConfigMapEntry(ConfigMap configMap, String key) {
		LOGGER.debug(() -> "Removing config map entry: " + key);
		ConfigMap newConfigMap = new ConfigMapBuilder(configMap).removeFromData(key).build();
		updateConfigMap(configMap, newConfigMap);
	}

	private void updateConfigMap(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
		String oldResourceVersion = oldConfigMap.getMetadata().getResourceVersion();
		kubernetesClient.configMaps()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.resource(newConfigMap)
			.lockResourceVersion(oldResourceVersion)
			.update();
	}

}
