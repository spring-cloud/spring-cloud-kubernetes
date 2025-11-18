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
import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * We acquire leadership, then lose it, then acquire it back.
 *
 * @author wind57
 */
@TestPropertySource(properties = { "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true",
		"readiness.passes=true",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.leader.election=debug"})
public class Fabric8LeaderElectionIsLostAndRestartedIT extends AbstractLeaderElection {

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll("drops-than-recovers");
	}

	@Test
	void test(CapturedOutput output) {

		// we have become the leader
		awaitUntil(30, 100, () -> output.getOut().contains("drops-than-recovers is the new leader"));

		// let's unwind some logs to see that the process is how we expect it to be

		// 1. lease is used as the lock
		assertThat(output.getOut()).contains("will use lease as the lock for leader election");

		// 2. wait for pod ready
		assertThat(output.getOut()).contains("will wait until pod drops-than-recovers is ready");
		assertThat(output.getOut())
			.contains("Pod : drops-than-recovers in namespace : default is not ready, will retry in one second");
		assertThat(output.getOut()).contains("Pod : drops-than-recovers in namespace : default is ready");

		// 3. we start leader initiator for our hostname (comes from our code)
		assertThat(output.getOut()).contains("starting leader initiator : drops-than-recovers");

		// 4. we try to acquire the lease (comes from fabric8 code)
		assertThat(output.getOut()).contains("Attempting to acquire leader lease 'LeaseLock: "
				+ "default - spring-k8s-leader-election-lock (drops-than-recovers)'");

		// 4. lease has been acquired
		assertThat(output.getOut())
			.contains("Acquired lease 'LeaseLock: default - spring-k8s-leader-election-lock (drops-than-recovers)'");

		// 5. we are the leader (comes from fabric8 code)
		assertThat(output.getOut()).contains("Leader changed from null to drops-than-recovers");

		// 6. wait until a renewal happens (comes from fabric code)
		// this one means that we have extended our leadership
		Awaitility.await()
			.atMost(Duration.ofSeconds(30))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut()
				.contains("Attempting to renew leader lease 'LeaseLock: "
						+ "default - spring-k8s-leader-election-lock (drops-than-recovers)'"));

		Lease lockLease = getLease();

		ZonedDateTime currentAcquiredTime = lockLease.getSpec().getAcquireTime();
		assertThat(currentAcquiredTime).isNotNull();
		assertThat(lockLease.getSpec().getLeaseDurationSeconds()).isEqualTo(6);
		assertThat(lockLease.getSpec().getLeaseTransitions()).isEqualTo(0);

		ZonedDateTime currentRenewalTime = lockLease.getSpec().getRenewTime();
		assertThat(currentRenewalTime).isNotNull();

		// 7. renewal happens
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(4)).until(() -> {
			ZonedDateTime newRenewalTime = getLease().getSpec().getRenewTime();
			return newRenewalTime.isAfter(currentRenewalTime);
		});

		// 8. simulate that leadership has changed
		Lease lease = getLease();
		lease.getSpec().setHolderIdentity("drops-than-recovers-is-not-the-leader-anymore");
		kubernetesClient.leases().inNamespace("default").resource(lease).update();

		// 9. leader has changed
		awaitUntil(10, 20, () -> output.getOut()
			.contains("Leader changed from drops-than-recovers to drops-than-recovers-is-not-the-leader-anymore"));

		// 10. our onNewLeaderCallback is triggered
		awaitUntil(10, 20,
				() -> output.getOut().contains("drops-than-recovers-is-not-the-leader-anymore is the new leader"));

		// 11. our onStopLeading callback is triggered
		awaitUntil(10, 20, () -> output.getOut().contains("drops-than-recovers stopped being a leader"));

		// 12. we gave up on leadership, so we will re-start the process
		awaitUntil(10, 20, () -> output.getOut()
			.contains("leaderFuture finished normally, will re-start it for : drops-than-recovers"));

		int leadershipFinished = output.getOut()
			.indexOf("leaderFuture finished normally, will re-start it for : drops-than-recovers");

		afterLeadershipRestart(output, leadershipFinished);

	}

	private void afterLeadershipRestart(CapturedOutput output, int leadershipFinished) {

		// 13. once we start leadership again, we try to acquire the new lock
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(leadershipFinished)
					.contains("Attempting to acquire leader lease 'LeaseLock: "
							+ "default - spring-k8s-leader-election-lock (drops-than-recovers)"));

		// 14. we can not acquire the new lock, since it did not yet expire
		// (the new leader is not going to renew it since it's an artificial leader)
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(leadershipFinished)
					.contains("Failed to acquire lease 'LeaseLock: "
							+ "default - spring-k8s-leader-election-lock (drops-than-recovers)' retrying..."));

		// 15. leader is again us
		awaitUntil(10, 500, () -> output.getOut()
			.substring(leadershipFinished)
			.contains("Leader changed from drops-than-recovers-is-not-the-leader-anymore to drops-than-recovers"));

		// 16. callback is again triggered
		awaitUntil(10, 500,
				() -> output.getOut()
					.substring(leadershipFinished)
					.contains("id : drops-than-recovers is the new leader"));

		// 17. the other callback is triggered also
		awaitUntil(10, 500,
				() -> output.getOut().substring(leadershipFinished).contains("drops-than-recovers is now a leader"));
	}

}
