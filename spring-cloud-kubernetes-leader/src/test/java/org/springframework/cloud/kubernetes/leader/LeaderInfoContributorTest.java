/*
 * Copyright 2019-2019 the original author or authors.
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

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.actuate.info.Info;
import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class LeaderInfoContributorTest {

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private Leader mockLeader;

	private LeaderInfoContributor leaderInfoContributor;

	@BeforeEach
	public void before() {
		this.leaderInfoContributor = new LeaderInfoContributor(this.mockLeadershipController, this.mockCandidate);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void infoWithoutLeader() {
		Info.Builder builder = new Info.Builder();

		leaderInfoContributor.contribute(builder);

		Map<String, Object> details = (Map<String, Object>) builder.build().get("leaderElection");
		assertThat(details).containsEntry("leaderId", "Unknown");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void infoWhenLeader() {
		given(this.mockLeadershipController.getLocalLeader()).willReturn(Optional.of(this.mockLeader));
		given(this.mockLeader.isCandidate(this.mockCandidate)).willReturn(true);
		given(this.mockLeader.getRole()).willReturn("testRole");
		given(this.mockLeader.getId()).willReturn("id");
		Info.Builder builder = new Info.Builder();

		leaderInfoContributor.contribute(builder);

		Map<String, Object> details = (Map<String, Object>) builder.build().get("leaderElection");
		assertThat(details).containsEntry("isLeader", true);
		assertThat(details).containsEntry("leaderId", "id");
		assertThat(details).containsEntry("role", "testRole");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void infoWhenAnotherIsLeader() {
		given(this.mockLeadershipController.getLocalLeader()).willReturn(Optional.of(this.mockLeader));
		given(this.mockLeader.getRole()).willReturn("testRole");
		given(this.mockLeader.getId()).willReturn("id");
		Info.Builder builder = new Info.Builder();

		leaderInfoContributor.contribute(builder);

		Map<String, Object> details = (Map<String, Object>) builder.build().get("leaderElection");
		assertThat(details).containsEntry("isLeader", false);
		assertThat(details).containsEntry("leaderId", "id");
		assertThat(details).containsEntry("role", "testRole");
	}

}
