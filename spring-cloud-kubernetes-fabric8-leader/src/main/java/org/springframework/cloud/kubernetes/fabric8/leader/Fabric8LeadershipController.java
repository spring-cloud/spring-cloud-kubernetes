/*
 * Copyright 2013-2019 the original author or authors.
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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.kubernetes.commons.leader.Leader;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeadershipController;
import org.springframework.cloud.kubernetes.commons.leader.PodReadinessWatcher;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author Gytis Trikleris
 */
public class Fabric8LeadershipController extends LeadershipController {

	private static final Logger LOGGER = LoggerFactory.getLogger(Fabric8LeadershipController.class);

	private final KubernetesClient kubernetesClient;

	public Fabric8LeadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher, KubernetesClient kubernetesClient) {
		super(candidate, leaderProperties, leaderEventPublisher);
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public synchronized void update() {
		LOGGER.debug("Checking leader state");
		ConfigMap configMap = getConfigMap();
		if (configMap == null && !leaderProperties.isCreateConfigMap()) {
			LOGGER.warn("ConfigMap '{}' does not exist and leaderProperties.isCreateConfigMap() "
					+ "is false, cannot acquire leadership", leaderProperties.getConfigMapName());
			notifyOnFailedToAcquire();
			return;
		}
		Leader leader = extractLeader(configMap);

		if (leader != null && isPodReady(leader.getId())) {
			handleLeaderChange(leader);
			return;
		}

		if (leader != null && leader.isCandidate(this.candidate)) {
			revoke(configMap);
		}
		else {
			acquire(configMap);
		}
	}

	public synchronized void revoke() {
		ConfigMap configMap = getConfigMap();
		Leader leader = extractLeader(configMap);

		if (leader != null && leader.isCandidate(this.candidate)) {
			revoke(configMap);
		}
	}

	private void revoke(ConfigMap configMap) {
		LOGGER.debug("Trying to revoke leadership for '{}'", this.candidate);

		try {
			String leaderKey = getLeaderKey();
			removeConfigMapEntry(configMap, leaderKey);
			handleLeaderChange(null);
		}
		catch (KubernetesClientException e) {
			LOGGER.warn("Failure when revoking leadership for '{}': {}", this.candidate, e.getMessage());
		}
	}

	private void acquire(ConfigMap configMap) {
		LOGGER.debug("Trying to acquire leadership for '{}'", this.candidate);

		if (!isPodReady(this.candidate.getId())) {
			LOGGER.debug("Pod of '{}' is not ready at the moment, cannot acquire leadership", this.candidate);
			return;
		}

		try {
			Map<String, String> data = getLeaderData(this.candidate);

			if (configMap == null) {
				createConfigMap(data);
			}
			else {
				updateConfigMapEntry(configMap, data);
			}

			Leader newLeader = new Leader(this.candidate.getRole(), this.candidate.getId());
			handleLeaderChange(newLeader);
		}
		catch (KubernetesClientException e) {
			LOGGER.warn("Failure when acquiring leadership for '{}': {}", this.candidate, e.getMessage());
			notifyOnFailedToAcquire();
		}
	}

	@Override
	protected PodReadinessWatcher createPodReadinessWatcher(String localLeaderId) {
		return new Fabric8PodReadinessWatcher(localLeaderId, this.kubernetesClient, this);
	}

	private Leader extractLeader(ConfigMap configMap) {
		if (configMap == null) {
			return null;
		}

		return extractLeader(configMap.getData());
	}

	private boolean isPodReady(String name) {
		return this.kubernetesClient.pods().withName(name).isReady();
	}

	private ConfigMap getConfigMap() {
		return this.kubernetesClient.configMaps()
				.inNamespace(this.leaderProperties.getNamespace(this.kubernetesClient.getNamespace()))
				.withName(this.leaderProperties.getConfigMapName()).get();
	}

	private void createConfigMap(Map<String, String> data) {
		LOGGER.debug("Creating new config map with data: {}", data);

		ConfigMap newConfigMap = new ConfigMapBuilder().withNewMetadata()
				.withName(this.leaderProperties.getConfigMapName()).addToLabels(PROVIDER_KEY, PROVIDER)
				.addToLabels(KIND_KEY, KIND).endMetadata().addToData(data).build();

		this.kubernetesClient.configMaps()
				.inNamespace(this.leaderProperties.getNamespace(this.kubernetesClient.getNamespace()))
				.resource(newConfigMap).create();
	}

	private void updateConfigMapEntry(ConfigMap configMap, Map<String, String> newData) {
		LOGGER.debug("Adding new data to config map: {}", newData);

		ConfigMap newConfigMap = new ConfigMapBuilder(configMap).addToData(newData).build();

		updateConfigMap(configMap, newConfigMap);
	}

	private void removeConfigMapEntry(ConfigMap configMap, String key) {
		LOGGER.debug("Removing config map entry '{}'", key);

		ConfigMap newConfigMap = new ConfigMapBuilder(configMap).removeFromData(key).build();

		updateConfigMap(configMap, newConfigMap);
	}

	private void updateConfigMap(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
		this.kubernetesClient.configMaps()
				.inNamespace(this.leaderProperties.getNamespace(this.kubernetesClient.getNamespace()))
				.resource(newConfigMap).lockResourceVersion(oldConfigMap.getMetadata().getResourceVersion()).replace();
	}

}
