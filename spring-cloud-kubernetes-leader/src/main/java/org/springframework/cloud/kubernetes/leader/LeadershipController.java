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

package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author Gytis Trikleris
 */
public class LeadershipController {

	private static final String PROVIDER_KEY = "provider";

	private static final String PROVIDER = "spring-cloud-kubernetes";

	private static final String KIND_KEY = "kind";

	private static final String KIND = "leaders";

	private static final Logger LOGGER = LoggerFactory.getLogger(LeadershipController.class);

	private final Candidate candidate;

	private final LeaderProperties leaderProperties;

	private final LeaderEventPublisher leaderEventPublisher;

	private final KubernetesClient kubernetesClient;

	private Leader localLeader;

	private PodReadinessWatcher leaderReadinessWatcher;

	public LeadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher, KubernetesClient kubernetesClient) {
		this.candidate = candidate;
		this.leaderProperties = leaderProperties;
		this.leaderEventPublisher = leaderEventPublisher;
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<Leader> getLocalLeader() {
		return Optional.ofNullable(this.localLeader);
	}

	public synchronized void update() {
		LOGGER.debug("Checking leader state");
		ConfigMap configMap = getConfigMap();
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

	private void handleLeaderChange(Leader newLeader) {
		if (Objects.equals(this.localLeader, newLeader)) {
			LOGGER.debug("Leader is still '{}'", this.localLeader);
			return;
		}

		Leader oldLeader = this.localLeader;
		this.localLeader = newLeader;

		if (oldLeader != null && oldLeader.isCandidate(this.candidate)) {
			notifyOnRevoked();
		}
		else if (newLeader != null && newLeader.isCandidate(this.candidate)) {
			notifyOnGranted();
		}

		restartLeaderReadinessWatcher();

		LOGGER.debug("New leader is '{}'", this.localLeader);
	}

	private void notifyOnGranted() {
		LOGGER.debug("Leadership has been granted for '{}'", this.candidate);

		Context context = new LeaderContext(this.candidate, this);
		this.leaderEventPublisher.publishOnGranted(this, context, this.candidate.getRole());
		try {
			this.candidate.onGranted(context);
		}
		catch (InterruptedException e) {
			LOGGER.warn(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private void notifyOnRevoked() {
		LOGGER.debug("Leadership has been revoked for '{}'", this.candidate);

		Context context = new LeaderContext(this.candidate, this);
		this.leaderEventPublisher.publishOnRevoked(this, context, this.candidate.getRole());
		this.candidate.onRevoked(context);
	}

	private void notifyOnFailedToAcquire() {
		if (this.leaderProperties.isPublishFailedEvents()) {
			Context context = new LeaderContext(this.candidate, this);
			this.leaderEventPublisher.publishOnFailedToAcquire(this, context, this.candidate.getRole());
		}
	}

	private void restartLeaderReadinessWatcher() {
		if (this.leaderReadinessWatcher != null) {
			this.leaderReadinessWatcher.stop();
			this.leaderReadinessWatcher = null;
		}

		if (this.localLeader != null && !this.localLeader.isCandidate(this.candidate)) {
			this.leaderReadinessWatcher = new PodReadinessWatcher(this.localLeader.getId(), this.kubernetesClient,
					this);
			this.leaderReadinessWatcher.start();
		}
	}

	private String getLeaderKey() {
		return this.leaderProperties.getLeaderIdPrefix() + this.candidate.getRole();
	}

	private Map<String, String> getLeaderData(Candidate candidate) {
		String leaderKey = getLeaderKey();
		return Collections.singletonMap(leaderKey, candidate.getId());
	}

	private Leader extractLeader(ConfigMap configMap) {
		if (configMap == null || configMap.getData() == null) {
			return null;
		}

		Map<String, String> data = configMap.getData();
		String leaderKey = getLeaderKey();
		String leaderId = data.get(leaderKey);
		if (leaderId == null) {
			return null;
		}

		return new Leader(this.candidate.getRole(), leaderId);
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
				.create(newConfigMap);
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
				.withName(this.leaderProperties.getConfigMapName())
				.lockResourceVersion(oldConfigMap.getMetadata().getResourceVersion()).replace(newConfigMap);
	}

}
