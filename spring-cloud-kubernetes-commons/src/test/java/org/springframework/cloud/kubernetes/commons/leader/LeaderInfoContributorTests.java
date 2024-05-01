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

package org.springframework.cloud.kubernetes.commons.leader;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.actuate.info.Info;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author wind57
 */
class LeaderInfoContributorTests {

	@Test
	void testLeaderMissing() {

		Candidate candidate = new DefaultCandidate("id", "role");
		LeaderProperties leaderProperties = new LeaderProperties();
		LeaderEventPublisher leaderEventPublisher = Mockito.mock(LeaderEventPublisher.class);
		LeadershipController leadershipController = new LeadershipControllerStub(candidate, leaderProperties,
				leaderEventPublisher);

		LeaderInfoContributor leaderInfoContributor = new LeaderInfoContributor(leadershipController, candidate);
		Info.Builder builder = new Info.Builder();
		leaderInfoContributor.contribute(builder);

		Assertions.assertEquals(builder.build().getDetails().get("leaderElection"), Map.of("leaderId", "Unknown"));
	}

	@Test
	void testLeaderPresentIsLeader() {

		Candidate candidate = new DefaultCandidate("leaderId", "leaderRole");
		LeaderProperties leaderProperties = new LeaderProperties();
		LeaderEventPublisher leaderEventPublisher = Mockito.mock(LeaderEventPublisher.class);
		LeadershipController leadershipController = new LeadershipControllerStub(candidate, leaderProperties,
				leaderEventPublisher);

		Leader leader = new Leader("leaderRole", "leaderId");

		leadershipController.handleLeaderChange(leader);

		LeaderInfoContributor leaderInfoContributor = new LeaderInfoContributor(leadershipController, candidate);
		Info.Builder builder = new Info.Builder();
		leaderInfoContributor.contribute(builder);

		Assertions.assertEquals(builder.build().getDetails().get("leaderElection"),
				Map.of("role", "leaderRole", "isLeader", true, "leaderId", "leaderId"));
	}

	@Test
	void testLeaderPresentIsNotLeader() {

		Candidate candidate = new DefaultCandidate("leaderId", "notLeaderRole");
		LeaderProperties leaderProperties = new LeaderProperties();
		LeaderEventPublisher leaderEventPublisher = Mockito.mock(LeaderEventPublisher.class);
		LeadershipController leadershipController = new LeadershipControllerStub(candidate, leaderProperties,
				leaderEventPublisher);

		Leader leader = new Leader("leaderRole", "leaderId");

		leadershipController.handleLeaderChange(leader);

		LeaderInfoContributor leaderInfoContributor = new LeaderInfoContributor(leadershipController, candidate);
		Info.Builder builder = new Info.Builder();
		leaderInfoContributor.contribute(builder);

		Assertions.assertEquals(builder.build().getDetails().get("leaderElection"),
				Map.of("role", "leaderRole", "isLeader", false, "leaderId", "leaderId"));
	}

}
