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

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readiness passes and we establish leadership
 *
 * @author wind57
 */
@TestPropertySource(properties = { "readiness.passes=true",
	"spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true" })
class Fabric8LeaderElectionReadinessPassesIT extends AbstractLeaderElection  {

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll("readiness-passes-simple-it");
	}

	/**
	 * <pre>
	 *     - readiness passes after 2 seconds
	 *     - leader election process happens after that
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		// we have become the leader
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("readiness-passes-simple-it is the new leader"));

		// let's unwind some logs to see that the process is how we expect it to be

		// 1. lease is used as the lock (comes from our code)
		assertThat(output.getOut()).contains("will use lease as the lock for leader election");

		// 2. leader initiator is started
		assertThat(output.getOut()).contains("starting leader initiator : readiness-passes-simple-it");

		// 3. wait for when pod is ready (we mock this one)
		assertThat(output.getOut()).contains("will wait until pod readiness-passes-simple-it is ready");

		// 4. we run readiness check in podReadyExecutor
		assertThat(output.getOut()).contains("Scheduling command to run in : podReadyExecutor");

		// 5. pod fails on the first two attempts
		assertThat(output.getOut())
			.contains("Pod : readiness-passes-simple-it in namespace : default is not ready, will retry in one second");

		// 6. readiness passes and pod is ready
		assertThat(output.getOut()).contains("Pod : readiness-passes-simple-it in namespace : default is ready");

		// 7. we cancel the scheduled future because we do not need it anymore
		assertThat(output.getOut()).contains("canceling scheduled future because readiness succeeded");

		// 8. executor is shutdown
		assertThat(output.getOut()).contains("Shutting down executor : podReadyExecutor");

		// 9. pod is now ready
		assertThat(output.getOut()).contains("readiness-passes-simple-it is ready");

		// 10. we are the leader
		assertThat(output.getOut()).contains("Leader changed from null to readiness-passes-simple-it");
	}

}
