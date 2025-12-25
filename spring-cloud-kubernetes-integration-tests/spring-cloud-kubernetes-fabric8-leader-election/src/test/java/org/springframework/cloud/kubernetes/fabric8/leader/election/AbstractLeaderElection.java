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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderUtils;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true",
				"spring.cloud.kubernetes.leader.election.lease-duration=6s",
				"spring.cloud.kubernetes.leader.election.renew-deadline=5s",
				"logging.level.org.springframework.cloud.kubernetes.commons.leader.election=debug",
				"logging.level.org.springframework.cloud.kubernetes.fabric8.leader.election=debug" },
		classes = { App.class, AbstractLeaderElection.LocalConfiguration.class })
@DirtiesContext
abstract class AbstractLeaderElection {

	private static K3sContainer container;

	private static MockedStatic<LeaderUtils> LEADER_UTILS_MOCKED_STATIC;

	@Autowired
	KubernetesClient kubernetesClient;

	static void beforeAll(String candidateIdentity) {
		container = Commons.container();
		container.start();

		LEADER_UTILS_MOCKED_STATIC = Mockito.mockStatic(LeaderUtils.class);
		LEADER_UTILS_MOCKED_STATIC.when(LeaderUtils::hostName).thenReturn(candidateIdentity);
	}

	@AfterAll
	static void afterAll() {
		LEADER_UTILS_MOCKED_STATIC.close();
	}

	void stopFutureAndDeleteLease(Fabric8LeaderElectionInitiator initiator) {

		initiator.preDestroy();

		kubernetesClient.leases()
			.inNamespace("default")
			.withName("spring-k8s-leader-election-lock")
			.withTimeout(10, TimeUnit.SECONDS)
			.delete();
	}

	Lease getLease() {
		return kubernetesClient.leases().inNamespace("default").withName("spring-k8s-leader-election-lock").get();
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

		// readiness passes after 2 retries
		@Bean
		@Primary
		@ConditionalOnProperty(value = "readiness.passes", havingValue = "true", matchIfMissing = false)
		BooleanSupplier readinessSupplierPasses() {
			AtomicInteger counter = new AtomicInteger(0);
			return () -> {
				if (counter.get() != 2) {
					counter.incrementAndGet();
					return false;
				}
				return true;
			};
		}

		// readiness fails after 2 retries
		@Bean
		@Primary
		@ConditionalOnProperty(value = "readiness.fails", havingValue = "true", matchIfMissing = false)
		BooleanSupplier readinessSupplierFails() {
			AtomicInteger counter = new AtomicInteger(0);
			return () -> {
				if (counter.get() != 2) {
					counter.incrementAndGet();
					return false;
				}
				throw new RuntimeException("readiness fails");
			};
		}

		// readiness always fails
		@Bean
		@Primary
		@ConditionalOnProperty(value = "readiness.never.finishes", havingValue = "true", matchIfMissing = false)
		BooleanSupplier readinessNeverFinishes() {
			return () -> false;
		}

	}

}
