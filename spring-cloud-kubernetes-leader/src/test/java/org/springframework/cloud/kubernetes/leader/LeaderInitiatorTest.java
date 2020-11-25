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

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
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

	@BeforeEach
	public void before() {
		this.leaderInitiator = new LeaderInitiator(this.mockLeaderProperties, this.mockLeadershipController,
				this.mockLeaderRecordWatcher, this.mockPodReadinessWatcher);
	}

	@AfterEach
	public void after() {
		this.leaderInitiator.stop();
	}

	@Test
	public void testIsAutoStartup() {
		given(this.mockLeaderProperties.isAutoStartup()).willReturn(true);

		assertThat(this.leaderInitiator.isAutoStartup()).isTrue();
	}

	@Test
	public void shouldStart() throws InterruptedException {
		given(this.mockLeaderProperties.getUpdatePeriod()).willReturn(Duration.ofMillis(1L));

		this.leaderInitiator.start();

		assertThat(this.leaderInitiator.isRunning()).isTrue();
		verify(this.mockLeaderRecordWatcher).start();
		verify(this.mockPodReadinessWatcher).start();
		Thread.sleep(10);
		verify(this.mockLeadershipController, atLeastOnce()).update();
	}

	@Test
	public void shouldStartOnlyOnce() {
		given(this.mockLeaderProperties.getUpdatePeriod()).willReturn(Duration.ofMillis(10000L));

		this.leaderInitiator.start();
		this.leaderInitiator.start();

		verify(this.mockLeaderRecordWatcher).start();
	}

	@Test
	public void shouldStop() {
		given(this.mockLeaderProperties.getUpdatePeriod()).willReturn(Duration.ofMillis(10000L));

		this.leaderInitiator.start();
		this.leaderInitiator.stop();

		assertThat(this.leaderInitiator.isRunning()).isFalse();
		verify(this.mockLeaderRecordWatcher).stop();
		verify(this.mockPodReadinessWatcher).start();
		verify(this.mockLeadershipController).revoke();
	}

	@Test
	public void shouldStopOnlyOnce() {
		given(this.mockLeaderProperties.getUpdatePeriod()).willReturn(Duration.ofMillis(10000L));

		this.leaderInitiator.start();
		this.leaderInitiator.stop();
		this.leaderInitiator.stop();

		verify(this.mockLeaderRecordWatcher).stop();
	}

	@Test
	public void shouldStopAndExecuteCallback() {
		given(this.mockLeaderProperties.getUpdatePeriod()).willReturn(Duration.ofMillis(10000L));

		this.leaderInitiator.start();
		this.leaderInitiator.stop(this.mockRunnable);

		assertThat(this.leaderInitiator.isRunning()).isFalse();
		verify(this.mockLeaderRecordWatcher).stop();
		verify(this.mockPodReadinessWatcher).start();
		verify(this.mockLeadershipController).revoke();
		verify(this.mockRunnable).run();
	}

}
