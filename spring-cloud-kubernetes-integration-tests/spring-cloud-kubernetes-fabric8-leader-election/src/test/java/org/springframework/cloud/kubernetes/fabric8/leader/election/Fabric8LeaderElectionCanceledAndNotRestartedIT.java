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

import static org.springframework.cloud.kubernetes.fabric8.leader.election.Assertions.assertAcquireAndRenew;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * <pre>
 *     - we acquire the leadership
 *     - leadership feature fails
 * </pre>
 *
 * @author wind57
 */
@TestPropertySource(properties = { "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true",
		"spring.cloud.kubernetes.leader.election.restart-on-failure=true", "readiness.passes=true" })
class Fabric8LeaderElectionCanceledAndNotRestartedIT extends AbstractLeaderElection {

	private static final String NAME = "acquired-then-canceled";

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

		initiator.leaderFeature().cancel(true);

		awaitUntil(10, 100, () -> output.getOut().contains("cancel was called on the leader initiator : " + NAME));

		// lease is going to reset
		awaitUntil(10, 100, () -> getLease().getSpec().getHolderIdentity().isEmpty());

	}

}
