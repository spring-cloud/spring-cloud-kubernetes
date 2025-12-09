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

import java.time.ZonedDateTime;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;

import org.springframework.boot.test.system.CapturedOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

final class Assertions {

	private Assertions() {

	}

	/**
	 * lease was acquired and we renewed it, at least once.
	 */
	static void assertAcquireAndRenew(CapturedOutput output, Supplier<Lease> leaseSupplier, String candidateIdentity) {
		// we have become the leader
		awaitUntil(60, 100, () -> output.getOut().contains(candidateIdentity + " is the new leader"));

		// let's unwind some logs to see that the process is how we expect it to be

		// 1. lease is used as the lock (comes from our code)
		assertThat(output.getOut()).contains("will use lease as the lock for leader election");

		// 2. we start leader initiator for our hostname (comes from our code)
		assertThat(output.getOut()).contains("starting leader initiator : " + candidateIdentity);

		// 3. we try to acquire the lease (comes from fabric8 code)
		assertThat(output.getOut()).contains(
			"Attempting to acquire leader lease 'LeaseLock: default - spring-k8s-leader-election-lock " +
				"(" + candidateIdentity + ")'");

		// 4. lease has been acquired
		assertThat(output.getOut())
			.contains("Acquired lease 'LeaseLock: default - spring-k8s-leader-election-lock " +
				"(" + candidateIdentity + ")'");

		// 5. we are the leader (comes from fabric8 code)
		assertThat(output.getOut()).contains("Leader changed from null to " + candidateIdentity);

		// 6. wait until a renewal happens (comes from fabric code)
		// this one means that we have extended our leadership
		awaitUntil(30, 500,
			() -> output.getOut()
				.contains("Attempting to renew leader lease 'LeaseLock: "
					+ "default - spring-k8s-leader-election-lock (" + candidateIdentity + ")'"));

		Lease lease = leaseSupplier.get();

		ZonedDateTime currentAcquiredTime = lease.getSpec().getAcquireTime();
		assertThat(currentAcquiredTime).isNotNull();
		assertThat(lease.getSpec().getLeaseDurationSeconds()).isEqualTo(6);
		assertThat(lease.getSpec().getLeaseTransitions()).isEqualTo(0);

		ZonedDateTime currentRenewalTime = lease.getSpec().getRenewTime();
		assertThat(currentRenewalTime).isNotNull();

		// 7. renewal happens
		awaitUntil(4, 500, () -> {
			ZonedDateTime newRenewalTime = leaseSupplier.get().getSpec().getRenewTime();
			return newRenewalTime.isAfter(currentRenewalTime);
		});
	}


}
