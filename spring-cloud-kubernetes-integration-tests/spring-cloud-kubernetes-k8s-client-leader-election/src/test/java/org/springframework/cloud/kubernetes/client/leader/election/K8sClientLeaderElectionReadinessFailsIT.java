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
 * Readiness fails with an Exception, and we don't establish leadership
 *
 * @author wind57
 */
@TestPropertySource(
	properties = { "readiness.fails=true", "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true" })
class K8sClientLeaderElectionReadinessFailsIT extends AbstractLeaderElection {

	private static final String NAME = "readiness-fails-it";

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

	/**
	 * <pre>
	 *   - readiness fails after 2 seconds - leader election process is not started at all
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		// we do not start leader election at all
		awaitUntil(60, 1000, () -> output.getOut()
			.contains("readiness failed for : " + NAME + ", leader election will not start"));

		// 1. lease is used as the lock (comes from our code)
		assertThat(output.getOut()).contains("will use lease as the lock for leader election");

		// 2. leader initiator is started
		assertThat(output.getOut()).contains("starting leader initiator : " + NAME);

		// 3. wait for when pod is ready (we mock this one)
		assertThat(output.getOut()).contains("will wait until pod " + NAME + " is ready");

		// 4. we run readiness check in podReadyExecutor
		assertThat(output.getOut()).contains("Scheduling command to run in : podReadyExecutor");

		// 5. pod fails on the first two attempts
		assertThat(output.getOut())
			.contains("Pod : " + NAME + " in namespace : default is not ready, will retry in one second");

		// 6. readiness fails
		assertThat(output.getOut()).contains("exception waiting for pod : " + NAME);

		// 7. readiness failed
		assertThat(output.getOut())
			.contains("pod readiness for : " + NAME + " failed with : readiness fails");

		// 8. we shut down the executor
		assertThat(output.getOut()).contains("canceling scheduled future because readiness failed");

		// 9. leader election did not even start properly
		assertThat(output.getOut())
			.contains("pod readiness for : " + NAME + " failed with : readiness fails");

		// 10. executor is shutdown, even when readiness failed
		awaitUntil(60, 100, () -> output.getOut()
			.contains("readiness failed for : " + NAME + ", leader election will not start"));

	}

}
