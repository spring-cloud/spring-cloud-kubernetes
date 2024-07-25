/*
 * Copyright 2013-2024 the original author or authors.
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
import java.util.function.Consumer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class Fabric8LeaderElectionConcurrentITTest {

	private static final LeaderElectionProperties PROPERTIES = new LeaderElectionProperties(false, false, 15, "default",
			"lease-lock", 10, 2);

	private static K3sContainer container;

	@BeforeAll
	static void beforeAll() {
		container = Commons.container();
		container.start();
	}

	@AfterAll
	static void afterAll() {
		container.stop();
	}

	@Test
	void test(CapturedOutput output) {

		String kubeConfigYaml = container.getKubeConfigYaml();
		Config config = Config.fromKubeconfig(kubeConfigYaml);
		KubernetesClient kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();

		String holderIdentityOne = "one";
		LeaderElectionConfig leaderElectionConfigOne = leaderElectionConfig(holderIdentityOne);
		Fabric8LeaderElectionInitiator one = new Fabric8LeaderElectionInitiator(holderIdentityOne, "default",
				kubernetesClient, leaderElectionConfigOne, PROPERTIES);

		String holderIdentityTwo = "two";
		LeaderElectionConfig leaderElectionConfigTwo = leaderElectionConfig(holderIdentityTwo);
		Fabric8LeaderElectionInitiator two = new Fabric8LeaderElectionInitiator(holderIdentityTwo, "default",
				kubernetesClient, leaderElectionConfigTwo, PROPERTIES);

		one.postConstruct();
		two.postConstruct();

		// both try to acquire the lock
		awaitForMessage(output, "Attempting to acquire leader lease 'LeaseLock: default - lease-lock (two)'...");
		awaitForMessage(output, "Attempting to acquire leader lease 'LeaseLock: default - lease-lock (one)'...");
		awaitForMessage(output, "Leader changed from null to ");

		boolean oneIsLeader = leaderElectionConfigOne.getLock().get(kubernetesClient).getHolderIdentity().equals("one");

		String currentLeader;
		String notLeader;

		if (oneIsLeader) {
			currentLeader = "one";
			notLeader = "two";
		}
		else {
			currentLeader = "two";
			notLeader = "one";
		}

		awaitForMessage(output, "Leader changed from null to " + currentLeader);
		awaitForMessage(output, "id : " + currentLeader + " is the new leader");
		awaitForMessage(output,
				"Successfully Acquired leader lease 'LeaseLock: " + "default - lease-lock (" + currentLeader + ")'");

		// renewal happens for the current leader
		awaitForMessage(output,
				"Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + currentLeader + ")'...");
		awaitForMessage(output, "Acquired lease 'LeaseLock: default - lease-lock (" + currentLeader + ")'");

		// the other elector says it can't acquire the lock
		awaitForMessage(output, "Lock is held by " + currentLeader + " and has not yet expired");
		awaitForMessage(output,
				"Failed to acquire lease 'LeaseLock: " + "default - lease-lock (" + notLeader + ")' retrying...");

		if (currentLeader.equals("one")) {
			one.preDestroy();
		}
		else {
			two.preDestroy();
		}

		// leader changed and the new leader can renew
		awaitForMessage(output, "id : " + notLeader + " is the new leader");
		awaitForMessage(output,
				"Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + notLeader + ")'...");
		awaitForMessage(output, "Acquired lease 'LeaseLock: default - lease-lock (" + notLeader + ")'");

	}

	private LeaderElectionConfig leaderElectionConfig(String holderIdentity) {

		LeaseLock lock = leaseLock(holderIdentity);
		Fabric8LeaderElectionCallbacks callbacks = callbacks(holderIdentity);

		return new LeaderElectionConfigBuilder().withReleaseOnCancel()
			.withName("leader-election-config")
			.withLeaseDuration(Duration.ofSeconds(PROPERTIES.leaseDuration()))
			.withLock(lock)
			.withRenewDeadline(Duration.ofSeconds(PROPERTIES.renewDeadline()))
			.withRetryPeriod(Duration.ofSeconds(PROPERTIES.retryPeriod()))
			.withLeaderCallbacks(callbacks)
			.build();
	}

	private LeaseLock leaseLock(String holderIdentity) {
		return new LeaseLock("default", "lease-lock", holderIdentity);
	}

	private Fabric8LeaderElectionCallbacks callbacks(String holderIdentity) {
		Fabric8LeaderElectionCallbacksAutoConfiguration configuration = new Fabric8LeaderElectionCallbacksAutoConfiguration();

		Runnable onStartLeadingCallback = configuration.onStartLeadingCallback(null, holderIdentity, PROPERTIES);
		Runnable onStopLeadingCallback = configuration.onStopLeadingCallback(null, holderIdentity, PROPERTIES);
		Consumer<String> onNewLeaderCallback = configuration.onNewLeaderCallback(null, PROPERTIES);

		return new Fabric8LeaderElectionCallbacks(onStartLeadingCallback, onStopLeadingCallback, onNewLeaderCallback);
	}

	private void awaitForMessage(CapturedOutput output, String message) {
		Awaitility.await()
			.pollInterval(Duration.ofMillis(100))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains(message));
	}

}
