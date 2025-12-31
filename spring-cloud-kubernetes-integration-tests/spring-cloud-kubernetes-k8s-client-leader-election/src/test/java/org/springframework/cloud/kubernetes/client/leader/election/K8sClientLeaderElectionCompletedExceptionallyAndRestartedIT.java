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

import static org.springframework.cloud.kubernetes.client.leader.election.Assertions.assertAcquireAndRenew;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * <pre>
 *     - we acquire the leadership
 *     - leadership feature fails
 *     - we retry and acquire it again
 * </pre>
 *
 * @author wind57
 */
@TestPropertySource(properties = { "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true",
		"spring.cloud.kubernetes.leader.election.restart-on-failure=true", "readiness.passes=true" })
class K8sClientLeaderElectionCompletedExceptionallyAndRestartedIT extends AbstractLeaderElection {

	private static final String NAME = "leader-completed-and-restarted-it";

	@Autowired
	private KubernetesClientLeaderElectionInitiator initiator;

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopLeaderAndDeleteLease(initiator, true);
	}

	@Test
	void test(CapturedOutput output) {

		assertAcquireAndRenew(output, this::getLease, NAME);

		// simulate that the lock is released
		initiator.leaderElector().close();

		// from the callback
		awaitUntil(5, 50, () -> output.getOut().contains("id : " + NAME + " stopped being a leader"));

		awaitUntil(5, 50, () -> output.getOut().contains("will re-start leader election for : " + NAME));

		int afterLeaderFailure = output.getOut().indexOf("will re-start leader election for : " + NAME);

		afterLeaderFailure(afterLeaderFailure, output);

	}

	private void afterLeaderFailure(int afterLeaderFailure, CapturedOutput output) {
		awaitUntil(60, 100, () -> output.getOut().substring(afterLeaderFailure).contains(NAME + " is the new leader"));
		awaitUntil(5, 100, () -> output.getOut().contains("Update lock to renew lease"));
		awaitUntil(5, 100, () -> output.getOut().contains("TryAcquireOrRenew return success"));
		awaitUntil(5, 100, () -> output.getOut().contains("Successfully renewed lease"));
		awaitUntil(5, 100, () -> output.getOut().contains("Update lock to renew lease"));
	}

}
