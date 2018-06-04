/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
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

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeadershipController {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeadershipController.class);

	private final LeaderProperties leaderProperties;

	private final KubernetesHelper kubernetesHelper;

	private final LeaderEventPublisher leaderEventPublisher;

	public LeadershipController(LeaderProperties leaderProperties, KubernetesHelper kubernetesHelper,
		LeaderEventPublisher leaderEventPublisher) {
		this.leaderProperties = leaderProperties;
		this.kubernetesHelper = kubernetesHelper;
		this.leaderEventPublisher = leaderEventPublisher;
	}

	public boolean acquire(Candidate candidate) {
		try {
			ConfigMap configMap = kubernetesHelper.getConfigMap();
			if (configMap == null) {
				createLeaderConfigMap(candidate);
				handleOnGranted(candidate);
				return true;
			}

			Leader leader = getLeader(candidate.getRole(), configMap);
			if (leader == null || !leader.isValid()) {
				updateLeaderConfigMap(candidate, configMap);
				handleOnGranted(candidate);
				return true;
			}

			if (candidate.getId().equals(leader.getId())) {
				return true;
			}
		} catch (KubernetesClientException e) {
			LOGGER.warn("Failed to acquire leadership with role='{}' for candidate='{}': {}", candidate.getRole(),
				candidate.getId(), e.getMessage());
		}

		handleOnFailed(candidate);
		return false;
	}

	public boolean revoke(Candidate candidate) {
		// Get config map
		// Check if candidate is a leader - if not, return false
		// Try to revoke leadership - return true
		// Return false
		// In case of an exception - pass it forward
		return false;
	}

	public Leader getLeader(String role) {
		try {
			ConfigMap configMap = kubernetesHelper.getConfigMap();
			if (configMap == null) {
				return null;
			}

			return getLeader(role, configMap);
		} catch (KubernetesClientException e) {
			LOGGER.warn("Failed to get leader for role='{}': {}", role, e.getMessage());
			return null;
		}
	}

	private Leader getLeader(String role, ConfigMap configMap) {
		if (configMap.getData() == null) {
			return null;
		}

		Map<String, String> data = configMap.getData();
		String leaderKey = leaderProperties.getLeaderIdPrefix() + role;
		String leaderId = data.get(leaderKey);
		if (leaderId == null) {
			return null;
		}

		return new Leader(role, leaderId, kubernetesHelper);
	}

	private void createLeaderConfigMap(Candidate candidate) {
		Map<String, String> data = getLeaderData(candidate);
		kubernetesHelper.createConfigMap(data);
	}

	private void updateLeaderConfigMap(Candidate candidate, ConfigMap configMap) {
		Map<String, String> data = getLeaderData(candidate);
		kubernetesHelper.updateConfigMap(configMap, data);
	}

	private void handleOnGranted(Candidate candidate) {
		leaderEventPublisher.publishOnGranted(this, null, candidate.getRole());
		try {
			candidate.onGranted(null); // TODO context
		} catch (InterruptedException e) {
			LOGGER.warn(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private void handleOnFailed(Candidate candidate) {
		leaderEventPublisher.publishOnFailedToAcquire(this, null, candidate.getRole());  // TODO context
	}

	private Map<String, String> getLeaderData(Candidate candidate) {
		String leaderKey = leaderProperties.getLeaderIdPrefix() + candidate.getRole();
		return Collections.singletonMap(leaderKey, candidate.getId());
	}

}
