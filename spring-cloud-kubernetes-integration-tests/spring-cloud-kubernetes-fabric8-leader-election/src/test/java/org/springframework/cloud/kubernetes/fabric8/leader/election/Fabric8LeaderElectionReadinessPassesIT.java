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
 * Readiness passes and we establish leadership
 *
 * @author wind57
 */
@TestPropertySource(
		properties = { "readiness.passes=true", "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true" })
class Fabric8LeaderElectionReadinessPassesIT extends AbstractLeaderElection {

	@Autowired
	private Fabric8LeaderElectionInitiator initiator;

	private static final String NAME = "readiness-passes-simple-it";

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopFutureAndDeleteLease(initiator);
	}

	/**
	 * <pre>
	 *     - readiness passes after 2 seconds
	 *     - leader election process happens after that
	 *     - we establish leadership and renew it
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		assertAcquireAndRenew(output, this::getLease, NAME);

		// 8. we cancel the scheduled future because we do not need it anymore
		assertThat(output.getOut()).contains("canceling scheduled future because readiness succeeded");

		// 9. executor is shutdown
		awaitUntil(60, 100, () -> output.getOut().contains("Shutting down executor : podReadyExecutor"));

		// 10. pod is now ready
		assertThat(output.getOut()).contains("readiness-passes-simple-it is ready");

		// 11. we are the leader
		assertThat(output.getOut()).contains("Leader changed from null to readiness-passes-simple-it");
	}

}
