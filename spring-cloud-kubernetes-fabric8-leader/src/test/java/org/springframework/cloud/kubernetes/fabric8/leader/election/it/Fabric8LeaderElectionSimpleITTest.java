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

package org.springframework.cloud.kubernetes.fabric8.leader.election.it;

import java.time.Duration;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A very simple test where we are the sole participant in the leader
 * election and everything goes fine from start to end. It's a happy path
 * scenario test.
 *
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true",
		"spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false" })
@ExtendWith(OutputCaptureExtension.class)
class Fabric8LeaderElectionSimpleITTest {

	private static K3sContainer container;

	private static final MockedStatic<LeaderUtils> LEADER_UTILS_MOCKED_STATIC = Mockito.mockStatic(LeaderUtils.class);

	@Autowired
	private KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAll() {
		container = Commons.container();
		container.start();

		LEADER_UTILS_MOCKED_STATIC.when(LeaderUtils::hostName).thenReturn("simple-it");
	}

	@AfterAll
	static void afterAll() {
		container.stop();
	}

	@Test
	void test(CapturedOutput output) {

		// 1. lease is used as the lock (comes from our code)
		assertThat(output.getOut()).contains(
			"will use lease as the lock for leader election");

		// 2. we start leader initiator for our hostname (comes from our code)
		assertThat(output.getOut()).contains(
			"starting leader initiator : simple-it");

		// 3. we try to acquire the lease (comes from fabric8 code)
		assertThat(output.getOut()).contains(
			"Attempting to acquire leader lease 'LeaseLock: default - spring-k8s-leader-election-lock (simple-it)'");

		// 4. we are the leader (comes from our code)
		assertThat(output.getOut()).contains("simple-it is now a leader");

		// wait for a renewal
		Awaitility.await()
			.pollInterval(Duration.ofSeconds(1))
			.atMost(Duration.ofMinutes(1))
			.until(() -> output.getOut().contains("Attempting to renew leader lease"));

//		// all these logs happen before a renewal
//		Assertions.assertThat(output.getOut()).contains("starting leader initiator");
//		Assertions.assertThat(output.getOut()).contains("Leader election started");
//		Assertions.assertThat(output.getOut()).contains("Successfully Acquired leader lease");
//
//		Lease lockLease = kubernetesClient.leases()
//			.inNamespace("default")
//			.withName("spring-k8s-leader-election-lock")
//			.get();
//		ZonedDateTime currentAcquiredTime = lockLease.getSpec().getAcquireTime();
//		Assertions.assertThat(currentAcquiredTime).isNotNull();
//		Assertions.assertThat(lockLease.getSpec().getLeaseDurationSeconds()).isEqualTo(15);
//		Assertions.assertThat(lockLease.getSpec().getLeaseTransitions()).isEqualTo(0);
//
//		ZonedDateTime currentRenewalTime = lockLease.getSpec().getRenewTime();
//		Assertions.assertThat(currentRenewalTime).isNotNull();
//
//		// renew happened, we renew by default on every two seconds
//		Awaitility.await()
//			.pollInterval(Duration.ofSeconds(1))
//			.atMost(Duration.ofSeconds(4))
//			.until(() -> !(currentRenewalTime.equals(kubernetesClient.leases()
//				.inNamespace("default")
//				.withName("spring-k8s-leader-election-lock")
//				.get()
//				.getSpec()
//				.getRenewTime())));
	}

	@TestConfiguration
	static class LocalConfiguration {

		@Bean
		@Primary
		KubernetesClient client() {
			String kubeConfigYaml = container.getKubeConfigYaml();
			Config config = Config.fromKubeconfig(kubeConfigYaml);
			return new KubernetesClientBuilder().withConfig(config).build();
		}

	}

	// test with pod ready
	// simulate that we lose leadership, must re-try

}
