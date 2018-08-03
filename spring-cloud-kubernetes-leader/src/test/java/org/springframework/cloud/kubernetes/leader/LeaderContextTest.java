/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.leader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderContextTest {

	private static final String ROLE = "test-role";

	private static final String ID = "test-id";

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private Leader mockLeader;

	private LeaderContext leaderContext;

	@Before
	public void before() {
		given(mockCandidate.getRole()).willReturn(ROLE);
		given(mockCandidate.getId()).willReturn(ID);

		leaderContext = new LeaderContext(mockCandidate, mockLeadershipController);
	}

	@Test
	public void testIsLeaderWithoutLeader() {
		boolean result = leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWithAnotherLeader() {
		given(mockLeadershipController.getLeader(ROLE)).willReturn(mockLeader);
		given(mockLeader.getId()).willReturn("another-test-id");

		boolean result = leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWhenLeader() {
		given(mockLeadershipController.getLeader(ROLE)).willReturn(mockLeader);
		given(mockLeader.getId()).willReturn(ID);

		boolean result = leaderContext.isLeader();

		assertThat(result).isTrue();
	}

	@Test
	public void shouldYieldLeadership() {
		leaderContext.yield();

		verify(mockLeadershipController).revoke(mockCandidate);
	}

}
