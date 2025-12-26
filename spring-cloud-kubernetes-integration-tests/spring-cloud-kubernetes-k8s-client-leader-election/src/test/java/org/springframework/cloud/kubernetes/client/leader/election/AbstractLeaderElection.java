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

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Lease;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.k3s.K3sContainer;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true",
		"spring.cloud.kubernetes.leader.election.lease-duration=6s",
		"spring.cloud.kubernetes.leader.election.renew-deadline=5s",
		"logging.level.org.springframework.cloud.kubernetes.commons.leader.election=debug",
		"logging.level.org.springframework.cloud.kubernetes.client.leader.election=debug",
		"logging.level.io.kubernetes.client.extended.leaderelection=debug" },
	classes = { App.class, AbstractLeaderElection.TestConfig.class,
		AbstractLeaderElection.PodReadyTestConfiguration.class })
@DirtiesContext
abstract class AbstractLeaderElection {

	@Autowired
	private ApiClient apiClient;

	private static K3sContainer container;

	private static MockedStatic<LeaderUtils> LEADER_UTILS_MOCKED_STATIC;

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

	void stopLeaderAndDeleteLease(KubernetesClientLeaderElectionInitiator initiator) {
		initiator.preDestroy();

		CoordinationV1Api api = new CoordinationV1Api(apiClient);

		try {
			api.deleteNamespacedLease("spring-k8s-leader-election-lock", "default").execute();
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	V1Lease getLease() {
		CoordinationV1Api api = new CoordinationV1Api(apiClient);
		try {
			return api.readNamespacedLease("spring-k8s-leader-election-lock", "default").execute();
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		ApiClient client() {
			String kubeConfigYaml = container.getKubeConfigYaml();

			ApiClient client;
			try {
				client = Config.fromConfig(new StringReader(kubeConfigYaml));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return client;
		}

	}

	@TestConfiguration
	static class PodReadyTestConfiguration {

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
