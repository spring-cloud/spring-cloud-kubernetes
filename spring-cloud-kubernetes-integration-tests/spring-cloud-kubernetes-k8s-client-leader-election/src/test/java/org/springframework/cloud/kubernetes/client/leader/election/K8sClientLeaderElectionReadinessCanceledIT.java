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

package org.springframework.cloud.kubernetes.client.leader.election;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * Readiness is canceled. This is the case when pod is shut down gracefully
 *
 * @author wind57
 */
@TestPropertySource(properties = { "readiness.never.finishes=true",
		"spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true" })
class K8sClientLeaderElectionReadinessCanceledIT extends AbstractLeaderElection {

	private static final String NAME = "readiness-canceled-it";

	@Autowired
	private KubernetesClientLeaderElectionInitiator initiator;

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopLeaderAndDeleteLease(initiator, false);
	}

	@Test
	void test(CapturedOutput output) {

		// we are trying readiness at least once
		awaitUntil(60, 500, () -> output.getOut()
			.contains("Pod : " + NAME + " in namespace : " + "default is not ready, will retry in one second"));

		initiator.preDestroy();

		// 1. preDestroy method logs what it will do
		assertThat(output.getOut()).contains("podReadyFuture will be canceled for : " + NAME);

		// 2. readiness failed
		assertThat(output.getOut()).contains("readiness failed for : " + NAME + ", leader election will not start");

		// 3. will cancel the future that is supposed to do the readiness
		assertThat(output.getOut()).contains("canceling scheduled future because completable future was cancelled");

		// 4. podReadyWaitingExecutor is shut down also
		assertThat(output.getOut()).contains("podReadyWaitingExecutor will be shutdown for : " + NAME);

		// 5. the scheduled executor where pod readiness is checked is shut down also
		awaitUntil(2, 100, () -> output.getOut().contains("Shutting down executor : podReadyExecutor"));

		// we need to call preDestroy again, to make sure that leaderFuture was not
		// started
		initiator.preDestroy();

		// 6. leader election is not started, since readiness does not finish
		assertThat(output.getOut()).doesNotContain("starting leader initiator :" + NAME);

	}

}
