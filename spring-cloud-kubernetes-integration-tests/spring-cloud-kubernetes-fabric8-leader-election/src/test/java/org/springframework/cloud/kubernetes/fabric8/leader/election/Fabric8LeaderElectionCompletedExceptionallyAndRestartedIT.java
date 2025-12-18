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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.fabric8.leader.election.Assertions.assertAcquireAndRenew;
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
class Fabric8LeaderElectionCompletedExceptionallyAndRestartedIT extends AbstractLeaderElection {

	private static final String NAME = "acquired-then-fails";

	@Autowired
	private Fabric8LeaderElectionInitiator initiator;

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopFutureAndDeleteLease(initiator);
	}

	@Test
	void test(CapturedOutput output) {

		assertAcquireAndRenew(output, this::getLease, NAME);

		initiator.leaderFeature().completeExceptionally(new RuntimeException("we kill the leadership future"));

		// from the callback
		awaitUntil(5, 50, () -> output.getOut().contains("id : acquired-then-fails stopped being a leader"));

		awaitUntil(5, 50, () -> output.getOut().contains("leader failed with : we kill the leadership future"));

		awaitUntil(5, 50, () -> output.getOut().contains("leader election failed for : acquired-then-fails"));

		int afterLeaderFailure = output.getOut().indexOf("leader election failed for : acquired-then-fails");

		afterLeaderFailure(afterLeaderFailure, output);
	}

	private void afterLeaderFailure(int afterLeaderFailure, CapturedOutput output) {
		awaitUntil(60, 100, () -> output.getOut().substring(afterLeaderFailure).contains(NAME + " is the new leader"));

		// lease has been re-acquired
		assertThat(output.getOut().substring(afterLeaderFailure))
			.contains("Acquired lease 'LeaseLock: default - spring-k8s-leader-election-lock " + "(" + NAME + ")'");

		// renewal happens (comes from fabric code)
		// this one means that we have extended our leadership
		awaitUntil(30, 500,
				() -> output.getOut()
					.substring(afterLeaderFailure)
					.contains("Attempting to renew leader lease 'LeaseLock: "
							+ "default - spring-k8s-leader-election-lock (" + NAME + ")'"));
	}

}
