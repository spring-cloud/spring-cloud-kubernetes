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

/**
 * A simple test where we are the sole participant in the leader election and everything
 * goes fine from start to end. It's a happy path scenario test.
 *
 * @author wind57
 */
@TestPropertySource(properties = "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false")
class Fabric8LeaderElectionSimpleIT extends AbstractLeaderElection {

	@Autowired
	private Fabric8LeaderElectionInitiator initiator;

	private static final String NAME = "simple-it";

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopFutureAndDeleteLease(initiator.leaderFeature());
	}

	/**
	 * <pre>
	 *     - readiness is not checked
	 *     - leader election process happens after that
	 *     - we establish leadership and renew it
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		assertAcquireAndRenew(output, this::getLease, NAME);
	}

}
