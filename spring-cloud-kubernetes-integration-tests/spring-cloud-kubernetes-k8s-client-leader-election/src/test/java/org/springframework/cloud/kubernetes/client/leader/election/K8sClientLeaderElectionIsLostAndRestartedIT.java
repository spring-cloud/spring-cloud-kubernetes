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

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.kubernetes.client.openapi.ApiClient;
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

//		// 9. leader has changed
//		awaitUntil(10, 20, () -> output.getOut()
//			.contains("Leader changed from " + NAME + " to leader-lost-then-recovers-it-is-not-the-leader-anymore"));
//
//		// 10. our onNewLeaderCallback is triggered
//		awaitUntil(10, 20,
//			() -> output.getOut().contains("leader-lost-then-recovers-it-is-not-the-leader-anymore is the new leader"));
//
//		// 11. our onStopLeading callback is triggered
//		awaitUntil(10, 20, () -> output.getOut().contains(NAME + " stopped being a leader"));
//
//		// 12. we gave up on leadership, so we will re-start the process
//		awaitUntil(10, 20, () -> output.getOut()
//			.contains("leaderFuture finished normally, will re-start it for : " + NAME));
//
//		int leadershipFinished = output.getOut()
//			.indexOf("leaderFuture finished normally, will re-start it for : " + NAME);
//
//		afterLeadershipRestart(output, leadershipFinished);

	}

	private void afterLeadershipRestart(CapturedOutput output, int leadershipFinished) {

		// 13. once we start leadership again, we try to acquire the new lock
		awaitUntil(10, 20,
			() -> output.getOut()
				.substring(leadershipFinished)
				.contains("Attempting to acquire leader lease 'LeaseLock: "
					+ "default - spring-k8s-leader-election-lock (" + NAME + ")"));

		// 14. we can not acquire the new lock, since it did not yet expire
		// (the new leader is not going to renew it since it's an artificial leader)
		awaitUntil(10, 20,
			() -> output.getOut()
				.substring(leadershipFinished)
				.contains("Failed to acquire lease 'LeaseLock: "
					+ "default - spring-k8s-leader-election-lock (" + NAME + ")' retrying..."));

		// 15. leader is again us
		awaitUntil(10, 500, () -> output.getOut()
			.substring(leadershipFinished)
			.contains("Leader changed from leader-lost-then-recovers-it-is-not-the-leader-anymore to " + NAME));

		// 16. callback is again triggered
		awaitUntil(10, 500,
			() -> output.getOut()
				.substring(leadershipFinished)
				.contains("id : " + NAME + " is the new leader"));

		// 17. the other callback is triggered also
		awaitUntil(10, 500,
			() -> output.getOut().substring(leadershipFinished).contains(NAME + " is now a leader"));
	}


}
