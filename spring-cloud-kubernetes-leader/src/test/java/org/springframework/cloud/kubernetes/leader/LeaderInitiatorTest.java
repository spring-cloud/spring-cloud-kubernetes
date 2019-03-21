/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.kubernetes.leader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

/**
 * @author Gytis Trikleris
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderInitiatorTest {

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private LeaderRecordWatcher mockLeaderRecordWatcher;

	@Mock
	private PodReadinessWatcher mockPodReadinessWatcher;

	@Mock
	private Runnable mockRunnable;

	private LeaderInitiator leaderInitiator;

	@Before
	public void before() {
		leaderInitiator = new LeaderInitiator(mockLeaderProperties, mockLeadershipController, mockLeaderRecordWatcher,
			mockPodReadinessWatcher);
	}

	@After
	public void after() {
		leaderInitiator.stop();
	}

	@Test
	public void testIsAutoStartup() {
		given(mockLeaderProperties.isAutoStartup()).willReturn(true);

		assertThat(leaderInitiator.isAutoStartup()).isTrue();
	}

	@Test
	public void shouldStart() throws InterruptedException {
		given(mockLeaderProperties.getUpdatePeriod()).willReturn(1L);

		leaderInitiator.start();

		assertThat(leaderInitiator.isRunning()).isTrue();
		verify(mockLeaderRecordWatcher).start();
		verify(mockPodReadinessWatcher).start();
		Thread.sleep(10);
		verify(mockLeadershipController, atLeastOnce()).update();
	}

	@Test
	public void shouldStartOnlyOnce() {
		given(mockLeaderProperties.getUpdatePeriod()).willReturn(10000L);

		leaderInitiator.start();
		leaderInitiator.start();

		verify(mockLeaderRecordWatcher).start();
	}

	@Test
	public void shouldStop() {
		given(mockLeaderProperties.getUpdatePeriod()).willReturn(10000L);

		leaderInitiator.start();
		leaderInitiator.stop();

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockLeaderRecordWatcher).stop();
		verify(mockPodReadinessWatcher).start();
		verify(mockLeadershipController).revoke();
	}

	@Test
	public void shouldStopOnlyOnce() {
		given(mockLeaderProperties.getUpdatePeriod()).willReturn(10000L);

		leaderInitiator.start();
		leaderInitiator.stop();
		leaderInitiator.stop();

		verify(mockLeaderRecordWatcher).stop();
	}

	@Test
	public void shouldStopAndExecuteCallback() {
		given(mockLeaderProperties.getUpdatePeriod()).willReturn(10000L);

		leaderInitiator.start();
		leaderInitiator.stop(mockRunnable);

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockLeaderRecordWatcher).stop();
		verify(mockPodReadinessWatcher).start();
		verify(mockLeadershipController).revoke();
		verify(mockRunnable).run();
	}
}
