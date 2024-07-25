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

package org.springframework.cloud.kubernetes.fabric8.leader;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.leader.Leader;
import org.springframework.cloud.kubernetes.commons.leader.LeaderContext;
import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
class Fabric8LeaderContextTest {

	private final Candidate mockCandidate = Mockito.mock(Candidate.class);

	private final Fabric8LeadershipController mockFabric8LeadershipController = Mockito
		.mock(Fabric8LeadershipController.class);

	private final Leader mockLeader = Mockito.mock(Leader.class);

	private LeaderContext leaderContext;

	@BeforeEach
	void beforeEach() {
		leaderContext = new LeaderContext(mockCandidate, mockFabric8LeadershipController);
	}

	@Test
	void testIsLeaderWithoutLeader() {
		Mockito.when(mockFabric8LeadershipController.getLocalLeader()).thenReturn(Optional.empty());
		boolean result = leaderContext.isLeader();
		assertThat(result).isFalse();
	}

	@Test
	void testIsLeaderWithAnotherLeader() {
		Mockito.when(mockFabric8LeadershipController.getLocalLeader()).thenReturn(Optional.of(mockLeader));
		boolean result = leaderContext.isLeader();
		assertThat(result).isFalse();
	}

	@Test
	void testIsLeaderWhenLeader() {
		Mockito.when(mockFabric8LeadershipController.getLocalLeader()).thenReturn(Optional.of(mockLeader));
		Mockito.when(mockLeader.isCandidate(mockCandidate)).thenReturn(true);
		boolean result = this.leaderContext.isLeader();
		assertThat(result).isTrue();
	}

	@Test
	void shouldYieldLeadership() {
		leaderContext.yield();
		verify(mockFabric8LeadershipController).revoke();
	}

}
