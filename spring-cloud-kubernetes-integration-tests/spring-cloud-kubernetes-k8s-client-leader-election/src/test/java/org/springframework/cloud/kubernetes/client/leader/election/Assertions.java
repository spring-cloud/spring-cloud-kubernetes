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

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.models.V1Lease;

import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
final class Assertions {

	private Assertions() {

	}

	/**
	 * lease was acquired and we renewed it, at least once.
	 */
	static void assertAcquireAndRenew(CapturedOutput output, Supplier<V1Lease> leaseSupplier,
			String candidateIdentity) {
		// we have become the leader
		awaitUntil(60, 100, () -> output.getOut().contains(candidateIdentity + " is the new leader"));

		// let's unwind some logs to see that the process is how we expect it to be

		// 1. lease is used as the lock (comes from our code)
		awaitUntil(5, 100, () -> output.getOut().contains("will use lease as the lock for leader election"));

		// 2. we start leader initiator for our hostname (comes from our code)
		awaitUntil(5, 100, () -> output.getOut().contains("starting leader initiator : " + candidateIdentity));

		// 3. start leader election with the configured lock
		awaitUntil(10, 100, () -> output.getOut()
			.contains("Start leader election with lock default/spring-k8s-leader-election-lock"));

		// 4. we try to acquire the lease
		awaitUntil(5, 100, () -> output.getOut().contains("Attempting to acquire leader lease"));

		// 5. lease has been acquired
		awaitUntil(5, 100,
				() -> output.getOut().contains("LeaderElection lock is currently held by " + candidateIdentity));

		// 6. we are the leader
		awaitUntil(10, 100, () -> output.getOut().contains("Successfully acquired lease, became leader"));

		// 7. wait until a renewal happens
		// this one means that we have extended our leadership
		awaitUntil(10, 100, () -> output.getOut().contains("Successfully renewed lease"));

		V1Lease lease = leaseSupplier.get();

		OffsetDateTime currentAcquiredTime = lease.getSpec().getAcquireTime();
		assertThat(currentAcquiredTime).isNotNull();
		assertThat(lease.getSpec().getLeaseDurationSeconds()).isEqualTo(6);
		assertThat(lease.getSpec().getLeaseTransitions()).isEqualTo(0);

		OffsetDateTime currentRenewalTime = lease.getSpec().getRenewTime();
		assertThat(currentRenewalTime).isNotNull();

		// 8. renewal happens
		awaitUntil(4, 500, () -> {
			OffsetDateTime newRenewalTime = leaseSupplier.get().getSpec().getRenewTime();
			return newRenewalTime.isAfter(currentRenewalTime);
		});
	}

}
