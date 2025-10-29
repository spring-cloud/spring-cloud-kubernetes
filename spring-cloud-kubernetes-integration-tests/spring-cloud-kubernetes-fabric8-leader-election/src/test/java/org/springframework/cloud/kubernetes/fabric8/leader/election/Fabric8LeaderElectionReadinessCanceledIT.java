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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readiness is canceled. This is the case when pod is shut down gracefully
 *
 * @author wind57
 */
@TestPropertySource(properties = {
	"readiness.cycle.false=true",
	"spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true"
})
class Fabric8LeaderElectionReadinessCanceledIT extends AbstractLeaderElection {

	@Autowired
	Fabric8LeaderElectionInitiator initiator;

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll("canceled-readiness-it");
	}

	@Test
	void test(CapturedOutput output) {

		// we are trying readiness at least once
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains(
				"Pod : canceled-readiness-it in namespace : default is not ready, will retry in one second"
			));

		initiator.preDestroy();

		try {
			Thread.sleep(2_000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		// 1. preDestroy logs what it will do
		assertThat(output.getOut()).contains("podReadyFuture will be canceled for : canceled-readiness-it");

		// 2. readiness failed
		assertThat(output.getOut()).contains(
			"readiness failed for : canceled-readiness-it, leader election will not start");

		// 3. will cancel the future that is supposed to do the readiness
		assertThat(output.getOut()).contains(
			"canceling scheduled future because completable future was cancelled");

		// 4. podReadyWaitingExecutor is shut down also
		assertThat(output.getOut()).contains(
			"podReadyWaitingExecutor will be shutdown for : canceled-readiness-it");

		// 5. the scheduled executor where pod readiness is checked is shut down also
		assertThat(output.getOut()).contains(
			"Shutting down executor : podReadyExecutor");

	}

}
