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

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeadershipController {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeadershipController.class);

	private final LeaderProperties leaderProperties;

	private final LeaderKubernetesHelper kubernetesHelper;

	private final LeaderEventPublisher leaderEventPublisher;

	public LeadershipController(LeaderProperties leaderProperties, LeaderKubernetesHelper kubernetesHelper,
		LeaderEventPublisher leaderEventPublisher) {
		this.leaderProperties = leaderProperties;
		this.kubernetesHelper = kubernetesHelper;
		this.leaderEventPublisher = leaderEventPublisher;
	}

	/**
	 * Acquire leadership for the requested candidate, if there is no existing leader or existing leader is not valid.
	 * <p>
	 * If leadership is successfully acquired {@code true} will be returned, {@link Candidate#onGranted(Context)}
	 * invoked and {@link LeaderEventPublisher#publishOnGranted(Object, Context, String)} event emitted.
	 * <p>
	 * If requested candidate is already a leader, simply {@code true} will be returned.
	 * <p>
	 * If for some reason leadership cannot be acquired (communication failure or there is another leader),
	 * {@code false} will be returned and {@link LeaderEventPublisher#publishOnFailedToAcquire(Object, Context, String)}
	 * event will be emitted.
	 *
	 * @param candidate
	 * @return {@code true} if at the end of execution candidate is a leader and {@code false} if it isn't.
	 */
	public boolean acquire(Candidate candidate) {
		try {
			ConfigMap configMap = kubernetesHelper.getConfigMap();
			if (configMap == null) {
				createConfigMapWithLeader(candidate);
				handleOnGranted(candidate);
				return true;
			}

			Leader leader = getLeader(candidate.getRole(), configMap);
			if (leader == null || !leader.isValid()) {
				updateLeaderInConfigMap(candidate, configMap);
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

	/**
	 * Revoke leadership for the requested candidate.
	 * <p>
	 * If candidate's leadership is successfully revoked, {@code true} will be returned,
	 * {@link Candidate#onRevoked(Context)} invoked and
	 * {@link LeaderEventPublisher#publishOnRevoked(Object, Context, String)} event emitted.
	 * <p>
	 * If requested candidate is already not a leader, simply {@code true} will be returned.
	 * <p>
	 * If leadership cannot be revoked for a communication error or a concurrent ConfigMap modification, {@code false}
	 * will be returned.
	 *
	 * @param candidate
	 * @return {@code true} if at the end of execution candidate is not a leader. {@code false} revoke operation failed.
	 */
	public boolean revoke(Candidate candidate) {
		try {
			ConfigMap configMap = kubernetesHelper.getConfigMap();
			if (configMap == null) {
				return true;
			}

			Leader leader = getLeader(candidate.getRole(), configMap);
			if (leader == null) {
				return true;
			}

			if (candidate.getId().equals(leader.getId())) {
				removeLeaderFromConfigMap(candidate, configMap);
				handleOnRevoked(candidate);
			}
		} catch (KubernetesClientException e) {
			LOGGER.warn("Failed to revoke leadership with role='{}' for candidate='{}': {}", candidate.getRole(),
				candidate.getId(), e.getMessage());
			return false;
		}

		return true;
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

	private void createConfigMapWithLeader(Candidate candidate) {
		Map<String, String> data = getLeaderData(candidate);
		kubernetesHelper.createConfigMap(data);
	}

	private void updateLeaderInConfigMap(Candidate candidate, ConfigMap configMap) {
		Map<String, String> data = getLeaderData(candidate);
		kubernetesHelper.updateConfigMapEntry(configMap, data);
	}

	private void removeLeaderFromConfigMap(Candidate candidate, ConfigMap configMap) {
		String leaderKey = leaderProperties.getLeaderIdPrefix() + candidate.getRole();
		kubernetesHelper.removeConfigMapEntry(configMap, leaderKey);
	}

	private void handleOnGranted(Candidate candidate) {
		Context context = new LeaderContext(candidate, this);
		leaderEventPublisher.publishOnGranted(this, context, candidate.getRole());
		try {
			candidate.onGranted(context);
		} catch (InterruptedException e) {
			LOGGER.warn(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private void handleOnRevoked(Candidate candidate) {
		Context context = new LeaderContext(candidate, this);
		leaderEventPublisher.publishOnRevoked(this, context, candidate.getRole());
		candidate.onRevoked(context);
	}

	private void handleOnFailed(Candidate candidate) {
		if (leaderProperties.isPublishFailedEvents()) {
			Context context = new LeaderContext(candidate, this);
			leaderEventPublisher.publishOnFailedToAcquire(this, context, candidate.getRole());
		}
	}

	private Map<String, String> getLeaderData(Candidate candidate) {
		String leaderKey = leaderProperties.getLeaderIdPrefix() + candidate.getRole();
		return Collections.singletonMap(leaderKey, candidate.getId());
	}

}
