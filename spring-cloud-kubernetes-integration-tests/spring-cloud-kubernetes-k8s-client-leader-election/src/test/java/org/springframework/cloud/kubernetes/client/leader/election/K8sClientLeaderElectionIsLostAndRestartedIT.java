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

import io.kubernetes.client.openapi.models.V1Lease;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.client.leader.election.Assertions.assertAcquireAndRenew;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * We acquire leadership, then lose it, then acquire it back. This tests the "leaderFuture
 * finished normally, will re-start it for" branch
 *
 * @author wind57
 */
@TestPropertySource(
		properties = { "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true", "readiness.passes=true" })
class K8sClientLeaderElectionIsLostAndRestartedIT extends AbstractLeaderElection {

	private static final String NAME = "leader-lost-then-recovers-it";

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

		// 8. simulate that leadership has changed
		V1Lease lease = getLease();
		lease.getSpec().setHolderIdentity("leader-lost-then-recovers-it-is-not-the-leader-anymore");
		updateLease(lease);

		// 9. we lost leadership
		awaitUntil(10, 100, () -> output.getOut().contains("Failed to renew lease, lose leadership"));

		// 10. callback confirms we lost leadership
		awaitUntil(10, 100, () -> output.getOut().contains("id : " + NAME + " stopped being a leader"));

		// 11. leader has changed
		awaitUntil(10, 20, () -> output.getOut()
			.contains(
					"LeaderElection lock is currently held by leader-lost-then-recovers-it-is-not-the-leader-anymore"));

		// 12. from our callback
		awaitUntil(10, 100, () -> output.getOut()
			.contains("id : leader-lost-then-recovers-it-is-not-the-leader-anymore is the new leader"));

		// 13. leadership is restarted for us
		awaitUntil(10, 100, () -> output.getOut().contains("will re-start leader election for : " + NAME));

		int leadershipFinished = output.getOut().indexOf("will re-start leader election for : " + NAME);
		afterLeadershipRestart(output, leadershipFinished);

	}

	private void afterLeadershipRestart(CapturedOutput output, int leadershipFinished) {

		// 14. since the new leader is artificial, renew is not going to happen for it
		awaitUntil(60, 100, () -> output.getOut().substring(leadershipFinished).contains(NAME + " is the new leader"));

		// 16. callback is again triggered
		awaitUntil(10, 100, () -> output.getOut().substring(leadershipFinished).contains(NAME + " is now a leader"));

	}

}
