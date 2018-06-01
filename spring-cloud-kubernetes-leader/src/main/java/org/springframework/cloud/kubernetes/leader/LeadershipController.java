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

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Candidate;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeadershipController {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeadershipController.class);

	private final LeaderProperties leaderProperties;

	private final KubernetesClient kubernetesClient;

	public LeadershipController(LeaderProperties leaderProperties, KubernetesClient kubernetesClient) {
		this.leaderProperties = leaderProperties;
		this.kubernetesClient = kubernetesClient;
	}

	public boolean acquire(Candidate candidate) {
		return false;
	}

	public boolean revoke(Candidate candidate) {
		return false;
	}

	public Leader getLeader(String role) {
		ConfigMap configMap = getConfigMap();
		if (configMap == null || configMap.getData() == null) {
			return null;
		}

		Map<String, String> data = configMap.getData();
		String leaderIdKey = leaderProperties.getLeaderIdPrefix() + role;
		String leaderId = data.get(leaderIdKey);
		if (leaderId == null) {
			return null;
		}

		return new Leader(role, leaderId);
	}

	private ConfigMap getConfigMap() {
		String namespace = leaderProperties.getNamespace(kubernetesClient.getNamespace());
		String name = leaderProperties.getConfigMapName();
		try {
			return kubernetesClient.configMaps()
				.inNamespace(namespace)
				.withName(name)
				.get();
		} catch (Exception e) {
			LOGGER.warn("Failed to get ConfigMap '{}' in the namespace '{}': {}", namespace, name, e.getMessage());
			return null;
		}
	}

}
