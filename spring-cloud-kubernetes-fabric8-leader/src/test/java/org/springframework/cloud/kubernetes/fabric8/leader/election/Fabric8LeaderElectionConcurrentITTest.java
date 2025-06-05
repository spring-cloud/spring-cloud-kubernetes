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
import org.mockito.Mockito;
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

	private static final LeaderElectionProperties PROPERTIES = new LeaderElectionProperties(false, false,
			Duration.ofSeconds(15), "default", "lease-lock", Duration.ofSeconds(5), Duration.ofSeconds(2),
			Duration.ofSeconds(5), false);

	private static K3sContainer container;

	private static final String HOLDER_IDENTITY_ONE = "one";

	private static final String HOLDER_IDENTITY_TWO = "two";

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

		LeaderElectionConfig leaderElectionConfigOne = leaderElectionConfig(HOLDER_IDENTITY_ONE);
		Fabric8LeaderElectionInitiator one = new Fabric8LeaderElectionInitiator(HOLDER_IDENTITY_ONE, "default",
				kubernetesClient, leaderElectionConfigOne, PROPERTIES);
		one = Mockito.spy(one);

		LeaderElectionConfig leaderElectionConfigTwo = leaderElectionConfig(HOLDER_IDENTITY_TWO);
		Fabric8LeaderElectionInitiator two = new Fabric8LeaderElectionInitiator(HOLDER_IDENTITY_TWO, "default",
				kubernetesClient, leaderElectionConfigTwo, PROPERTIES);
		two = Mockito.spy(two);

		one.postConstruct();
		two.postConstruct();

		// both try to acquire the lock
		awaitForMessage(output, "Attempting to acquire leader lease 'LeaseLock: default - lease-lock (two)'...");
		awaitForMessage(output, "Attempting to acquire leader lease 'LeaseLock: default - lease-lock (one)'...");
		awaitForMessage(output, "Leader changed from null to ");

		LeaderAndFollower leaderAndFollower = leaderAndFollower(leaderElectionConfigOne, kubernetesClient);
		String leader = leaderAndFollower.leader();
		String follower = leaderAndFollower.follower();

		awaitForMessage(output, "Leader changed from null to " + leader);
		awaitForMessage(output, "id : " + leader + " is the new leader");
		awaitForMessage(output,
				"Successfully Acquired leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'");

		// renewal happens for the current leader
		awaitForMessage(output,
				"Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'...");
		awaitForMessage(output, "Acquired lease 'LeaseLock: default - lease-lock (" + leader + ")'");

		// the other elector says it can't acquire the lock
		awaitForMessage(output, "Lock is held by " + leader + " and has not yet expired");
		awaitForMessage(output,
				"Failed to acquire lease 'LeaseLock: " + "default - lease-lock (" + follower + ")' retrying...");

		int beforeRelease = output.getOut().length();
		failLeaderRenewal(leader, one, two);

		/*
		 * we simulated above that renewal failed and leader future was canceled. In this
		 * case, the 'notLeader' picks up the leadership, the 'leader' is now a
		 * "follower", it re-tries to take leadership.
		 */
		awaitForMessageFromPosition(output, beforeRelease, "id : " + follower + " is the new leader");
		awaitForMessageFromPosition(output, beforeRelease,
				"Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + follower + ")'...");
		awaitForMessageFromPosition(output, beforeRelease,
				"Acquired lease 'LeaseLock: default - lease-lock (" + follower + ")'");

		// proves that the canceled leader tries to acquire again the leadership
		awaitForMessageFromPosition(output, beforeRelease,
				"Attempting to acquire leader lease 'LeaseLock: default - lease-lock (" + leader + ")'...");
		awaitForMessageFromPosition(output, beforeRelease, "Lock is held by " + follower + " and has not yet expired");

		/*
		 * we simulate the renewal failure one more time. we know that leader = 'follower'
		 */
		beforeRelease = output.getOut().length();
		failLeaderRenewal(follower, one, two);

		awaitForMessageFromPosition(output, beforeRelease, "id : " + leader + " is the new leader");
		awaitForMessageFromPosition(output, beforeRelease,
				"Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'...");
		awaitForMessageFromPosition(output, beforeRelease,
				"Acquired lease 'LeaseLock: default - lease-lock (" + leader + ")'");

		// proves that the canceled leader tries to acquire again the leadership
		awaitForMessageFromPosition(output, beforeRelease,
				"Attempting to acquire leader lease 'LeaseLock: default - lease-lock (" + follower + ")'...");
		awaitForMessageFromPosition(output, beforeRelease, "Lock is held by " + leader + " and has not yet expired");

	}

	/**
	 * <pre>
	 * 		simulate that renewal failed, we do this by:
	 * 			- calling preDestroy, thus calling future::cancel
	 * 		      (same as fabric8 internals will do)
	 * 		    - do not shutdown the executor
	 * </pre>
	 */
	private void assumeRenewalFailed(Fabric8LeaderElectionInitiator initiator) {
		Mockito.doNothing().when(initiator).destroyCalled();
		Mockito.doNothing().when(initiator).shutDownExecutor();
	}

	private LeaderElectionConfig leaderElectionConfig(String holderIdentity) {

		LeaseLock lock = leaseLock(holderIdentity);
		Fabric8LeaderElectionCallbacks callbacks = callbacks(holderIdentity);

		return new LeaderElectionConfigBuilder().withReleaseOnCancel()
			.withName("leader-election-config")
			.withLeaseDuration(PROPERTIES.leaseDuration())
			.withLock(lock)
			.withRenewDeadline(PROPERTIES.renewDeadline())
			.withRetryPeriod(PROPERTIES.retryPeriod())
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

	private void awaitForMessageFromPosition(CapturedOutput output, int from, String message) {
		Awaitility.await()
			.pollInterval(Duration.ofMillis(100))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().substring(from).contains(message));
	}

	private LeaderAndFollower leaderAndFollower(LeaderElectionConfig leaderElectionConfig,
			KubernetesClient kubernetesClient) {
		boolean oneIsLeader = leaderElectionConfig.getLock()
			.get(kubernetesClient)
			.getHolderIdentity()
			.equals(HOLDER_IDENTITY_ONE);

		if (oneIsLeader) {
			return new LeaderAndFollower("one", "two");
		}
		else {
			return new LeaderAndFollower("two", "one");
		}
	}

	private void failLeaderRenewal(String currentLeader, Fabric8LeaderElectionInitiator one,
			Fabric8LeaderElectionInitiator two) {
		if (currentLeader.equals("one")) {
			assumeRenewalFailed(one);
			one.preDestroy();
		}
		else {
			assumeRenewalFailed(two);
			two.preDestroy();
		}
	}

	private record LeaderAndFollower(String leader, String follower) {

	}

}
