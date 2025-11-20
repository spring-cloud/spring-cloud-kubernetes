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

/**
 * A simple test where we are the sole participant in the leader election and everything
 * goes fine from start to end. It's a happy path scenario test.
 *
 * @author wind57
 */
@TestPropertySource(properties = "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false")
class Fabric8LeaderElectionSimpleIT extends AbstractLeaderElection {

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll("simple-it");
	}

	@Test
	void test(CapturedOutput output) {

		// we have become the leader
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("simple-it is the new leader"));

		// let's unwind some logs to see that the process is how we expect it to be

		// 1. lease is used as the lock (comes from our code)
		assertThat(output.getOut()).contains("will use lease as the lock for leader election");

		// 2. we start leader initiator for our hostname (comes from our code)
		assertThat(output.getOut()).contains("starting leader initiator : simple-it");

		// 3. we try to acquire the lease (comes from fabric8 code)
		assertThat(output.getOut()).contains(
				"Attempting to acquire leader lease 'LeaseLock: default - spring-k8s-leader-election-lock (simple-it)'");

		// 4. lease has been acquired
		assertThat(output.getOut())
			.contains("Acquired lease 'LeaseLock: default - spring-k8s-leader-election-lock (simple-it)'");

		// 5. we are the leader (comes from fabric8 code)
		assertThat(output.getOut()).contains("Leader changed from null to simple-it");

		// 6. wait until a renewal happens (comes from fabric code)
		// this one means that we have extended our leadership
		Awaitility.await()
			.atMost(Duration.ofSeconds(30))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> output.getOut()
				.contains(
						"Attempting to renew leader lease 'LeaseLock: default - spring-k8s-leader-election-lock (simple-it)'"));

		Lease lockLease = getLease();

		ZonedDateTime currentAcquiredTime = lockLease.getSpec().getAcquireTime();
		assertThat(currentAcquiredTime).isNotNull();
		assertThat(lockLease.getSpec().getLeaseDurationSeconds()).isEqualTo(15);
		assertThat(lockLease.getSpec().getLeaseTransitions()).isEqualTo(0);

		ZonedDateTime currentRenewalTime = lockLease.getSpec().getRenewTime();
		assertThat(currentRenewalTime).isNotNull();

		// 7. renewal happens
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(4)).until(() -> {
			ZonedDateTime newRenewalTime = getLease().getSpec().getRenewTime();
			return newRenewalTime.isAfter(currentRenewalTime);
		});

	}

	private Lease getLease() {
		return kubernetesClient.leases().inNamespace("default").withName("spring-k8s-leader-election-lock").get();
	}

}
