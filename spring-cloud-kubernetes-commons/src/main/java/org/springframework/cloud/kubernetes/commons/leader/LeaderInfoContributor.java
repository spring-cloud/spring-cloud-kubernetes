/*
 * Copyright 2019-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.integration.leader.Candidate;

public class LeaderInfoContributor implements InfoContributor {

	private final LeadershipController leadershipController;

	private final Candidate candidate;

	public LeaderInfoContributor(LeadershipController leadershipController, Candidate candidate) {
		this.leadershipController = leadershipController;
		this.candidate = candidate;
	}

	@Override
	public void contribute(Builder builder) {
		Map<String, Object> details = new HashMap<>();
		leadershipController.getLocalLeader().ifPresentOrElse(leader -> {
			details.put("leaderId", leader.getId());
			details.put("role", leader.getRole());
			details.put("isLeader", leader.isCandidate(candidate));
		}, () -> details.put("leaderId", "Unknown"));

		builder.withDetail("leaderElection", details);
	}

}
