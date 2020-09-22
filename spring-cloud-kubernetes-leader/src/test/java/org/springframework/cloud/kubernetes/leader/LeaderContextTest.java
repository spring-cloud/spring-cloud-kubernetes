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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
public class LeaderContextTest {

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private Leader mockLeader;

	private LeaderContext leaderContext;

	@BeforeEach
	public void before() {
		this.leaderContext = new LeaderContext(this.mockCandidate, this.mockLeadershipController);
	}

	@Test
	public void testIsLeaderWithoutLeader() {
		given(this.mockLeadershipController.getLocalLeader()).willReturn(Optional.empty());

		boolean result = this.leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWithAnotherLeader() {
		given(this.mockLeadershipController.getLocalLeader()).willReturn(Optional.of(this.mockLeader));

		boolean result = this.leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWhenLeader() {
		given(this.mockLeadershipController.getLocalLeader()).willReturn(Optional.of(this.mockLeader));
		given(this.mockLeader.isCandidate(this.mockCandidate)).willReturn(true);

		boolean result = this.leaderContext.isLeader();

		assertThat(result).isTrue();
	}

	@Test
	public void shouldYieldLeadership() {
		this.leaderContext.yield();

		verify(this.mockLeadershipController).revoke();
	}

}
