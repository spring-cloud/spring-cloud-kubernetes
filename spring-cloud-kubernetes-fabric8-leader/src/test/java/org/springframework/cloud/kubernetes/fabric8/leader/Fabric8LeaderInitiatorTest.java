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

package org.springframework.cloud.kubernetes.fabric8.leader;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.leader.LeaderInitiator;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

/**
 * @author Gytis Trikleris
 */
class Fabric8LeaderInitiatorTest {

	private final LeaderProperties leaderProperties = new LeaderProperties();

	private final Fabric8LeadershipController mockFabric8LeadershipController = Mockito
		.mock(Fabric8LeadershipController.class);

	private final Fabric8LeaderRecordWatcher mockFabric8LeaderRecordWatcher = Mockito
		.mock(Fabric8LeaderRecordWatcher.class);

	private final Fabric8PodReadinessWatcher mockFabric8PodReadinessWatcher = Mockito
		.mock(Fabric8PodReadinessWatcher.class);

	private final Runnable runnable = Mockito.mock(Runnable.class);

	private LeaderInitiator leaderInitiator;

	@BeforeEach
	void beforeEach() {
		leaderInitiator = new LeaderInitiator(leaderProperties, mockFabric8LeadershipController,
				mockFabric8LeaderRecordWatcher, mockFabric8PodReadinessWatcher);
	}

	@AfterEach
	void afterEach() {
		leaderInitiator.stop();
	}

	@Test
	void testIsAutoStartup() {
		assertThat(leaderInitiator.isAutoStartup()).isTrue();
	}

	@Test
	void shouldStart() {
		leaderProperties.setUpdatePeriod(Duration.ofMillis(1L));

		leaderInitiator.start();

		assertThat(leaderInitiator.isRunning()).isTrue();
		verify(mockFabric8LeaderRecordWatcher).start();
		verify(mockFabric8PodReadinessWatcher).start();
		boolean[] updateCalled = new boolean[1];
		Mockito.doAnswer(x -> {
			updateCalled[0] = true;
			return null;
		}).when(mockFabric8LeadershipController).update();

		Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> updateCalled[0]);

		verify(mockFabric8LeadershipController, atLeastOnce()).update();
	}

	@Test
	void shouldStartOnlyOnce() {
		leaderInitiator.start();
		leaderInitiator.start();

		verify(mockFabric8LeaderRecordWatcher).start();
	}

	@Test
	void shouldStop() {
		leaderInitiator.start();
		leaderInitiator.stop();

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockFabric8LeaderRecordWatcher).stop();
		verify(mockFabric8PodReadinessWatcher).start();
		verify(mockFabric8LeadershipController).revoke();
	}

	@Test
	void shouldStopOnlyOnce() {
		leaderInitiator.start();
		leaderInitiator.stop();
		leaderInitiator.stop();

		verify(mockFabric8LeaderRecordWatcher).stop();
	}

	@Test
	void shouldStopAndExecuteCallback() {
		leaderInitiator.start();
		leaderInitiator.stop(runnable);

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockFabric8LeaderRecordWatcher).stop();
		verify(mockFabric8PodReadinessWatcher).start();
		verify(mockFabric8LeadershipController).revoke();
		verify(runnable).run();
	}

}
