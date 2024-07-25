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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * @author wind57
 */
final class Fabric8LeaderElectionInfoContributor implements InfoContributor {

	private final String holderIdentity;

	private final LeaderElectionConfig leaderElectionConfig;

	private final KubernetesClient fabric8KubernetesClient;

	Fabric8LeaderElectionInfoContributor(String holderIdentity, LeaderElectionConfig leaderElectionConfig,
			KubernetesClient fabric8KubernetesClient) {
		this.holderIdentity = holderIdentity;
		this.leaderElectionConfig = leaderElectionConfig;
		this.fabric8KubernetesClient = fabric8KubernetesClient;
	}

	@Override
	public void contribute(Info.Builder builder) {
		Map<String, Object> details = new HashMap<>();
		Optional.ofNullable(leaderElectionConfig.getLock().get(fabric8KubernetesClient))
			.ifPresentOrElse(leaderRecord -> {
				boolean isLeader = holderIdentity.equals(leaderRecord.getHolderIdentity());
				details.put("leaderId", holderIdentity);
				details.put("isLeader", isLeader);
			}, () -> details.put("leaderId", "Unknown"));

		builder.withDetail("leaderElection", details);
	}

}
