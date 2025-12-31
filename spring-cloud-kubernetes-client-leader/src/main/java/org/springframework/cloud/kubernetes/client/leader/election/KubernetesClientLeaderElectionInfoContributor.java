/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.leader.election;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.openapi.ApiException;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
final class KubernetesClientLeaderElectionInfoContributor implements InfoContributor {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientLeaderElectionInfoContributor.class);

	private final String candidateIdentity;

	private final LeaderElectionConfig leaderElectionConfig;

	KubernetesClientLeaderElectionInfoContributor(String candidateIdentity, LeaderElectionConfig leaderElectionConfig) {
		this.candidateIdentity = candidateIdentity;
		this.leaderElectionConfig = leaderElectionConfig;
	}

	@Override
	public void contribute(Info.Builder builder) {
		Map<String, Object> details = new HashMap<>();
		try {
			Optional.ofNullable(leaderElectionConfig.getLock().get()).ifPresentOrElse(leaderRecord -> {
				boolean isLeader = candidateIdentity.equals(leaderRecord.getHolderIdentity());
				details.put("leaderId", candidateIdentity);
				details.put("isLeader", isLeader);
			}, () -> details.put("leaderId", "Unknown"));
		}
		catch (ApiException e) {
			LOG.error(e, "error in leader election info contributor");
		}

		builder.withDetail("leaderElection", details);
	}

}
