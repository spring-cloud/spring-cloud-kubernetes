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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class Fabric8LeaderElectionConcurrentIT {

	private static final String LEASE_NAME = "lease-lock";

	private static final LeaderElectionProperties PROPERTIES = new LeaderElectionProperties(false, false,
			Duration.ofSeconds(15), "default", LEASE_NAME, Duration.ofSeconds(5), Duration.ofSeconds(2),
			Duration.ofSeconds(5), false, true);

	private static final String CANDIDATE_IDENTITY_ONE = "one";

	private static final String CANDIDATE_IDENTITY_TWO = "two";

	private Fabric8LeaderElectionInitiator one;

	private Fabric8LeaderElectionInitiator two;

	private static KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAll() {

		K3sContainer container = Commons.container();
		container.start();

		String kubeConfigYaml = container.getKubeConfigYaml();
		Config config = Config.fromKubeconfig(kubeConfigYaml);
		kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();

	}

	@AfterAll
	static void afterAll() {
		kubernetesClient.leases()
			.inNamespace("default")
			.withName(LEASE_NAME)
			.withTimeout(10, TimeUnit.SECONDS)
			.delete();
	}

	@AfterEach
	void afterEach() {
		one.preDestroy();
		two.preDestroy();

		kubernetesClient.leases()
			.inNamespace("default")
			.withName(LEASE_NAME)
			.withTimeout(10, TimeUnit.SECONDS)
			.delete();
	}

	@Test
	void test(CapturedOutput output) {

		LeaderElectionConfig leaderElectionConfigOne = leaderElectionConfig(CANDIDATE_IDENTITY_ONE);
		one = new Fabric8LeaderElectionInitiator(CANDIDATE_IDENTITY_ONE, "default", kubernetesClient,
				leaderElectionConfigOne, PROPERTIES, () -> true);

		LeaderElectionConfig leaderElectionConfigTwo = leaderElectionConfig(CANDIDATE_IDENTITY_TWO);
		two = new Fabric8LeaderElectionInitiator(CANDIDATE_IDENTITY_TWO, "default", kubernetesClient,
				leaderElectionConfigTwo, PROPERTIES, () -> true);

		one.postConstruct();
		two.postConstruct();

		// both try to acquire the lock
		awaitUntil(3, 100, () -> output.getOut()
			.contains("Attempting to acquire leader lease 'LeaseLock: default - lease-lock (two)'..."));
		awaitUntil(3, 100, () -> output.getOut()
			.contains("Attempting to acquire leader lease 'LeaseLock: default - lease-lock (one)'..."));
		awaitUntil(3, 100, () -> output.getOut().contains("Leader changed from null to "));

		LeaderAndFollower leaderAndFollower = leaderAndFollower(leaderElectionConfigOne, kubernetesClient);
		String leader = leaderAndFollower.leader();
		String follower = leaderAndFollower.follower();

		awaitUntil(3, 100, () -> output.getOut().contains("Leader changed from null to " + leader));
		awaitUntil(3, 100, () -> output.getOut().contains("id : " + leader + " is the new leader"));
		awaitUntil(3, 100, () -> output.getOut()
			.contains("Successfully Acquired leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'"));

		// renewal happens for the current leader
		awaitUntil(3, 100, () -> output.getOut()
			.contains("Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'..."));
		awaitUntil(3, 100,
				() -> output.getOut().contains("Acquired lease 'LeaseLock: default - lease-lock (" + leader + ")'"));

		// the other elector says it can't acquire the lock
		awaitUntil(10, 100, () -> output.getOut().contains("Lock is held by " + leader + " and has not yet expired"));
		awaitUntil(3, 100, () -> output.getOut()
			.contains("Failed to acquire lease 'LeaseLock: " + "default - lease-lock (" + follower + ")' retrying..."));

		int beforeRelease = output.getOut().length();
		failLeaderRenewal(leader, one, two);

		/*
		 * we simulated above that renewal failed and leader future was canceled. In this
		 * case, the 'notLeader' picks up the leadership, the 'leader' is now a
		 * "follower", it re-tries to take leadership.
		 */
		awaitUntil(10, 100,
				() -> output.getOut().substring(beforeRelease).contains("id : " + follower + " is the new leader"));
		awaitUntil(3, 100, () -> output.getOut()
			.substring(beforeRelease)
			.contains("Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + follower + ")'..."));
		awaitUntil(3, 100,
				() -> output.getOut()
					.substring(beforeRelease)
					.contains("Acquired lease 'LeaseLock: default - lease-lock (" + follower + ")'"));

		// proves that the canceled leader tries to acquire again the leadership
		awaitUntil(3, 100, () -> output.getOut()
			.substring(beforeRelease)
			.contains("Attempting to acquire leader lease 'LeaseLock: default - lease-lock (" + leader + ")'..."));
		awaitUntil(3, 100,
				() -> output.getOut()
					.substring(beforeRelease)
					.contains("Lock is held by " + follower + " and has not yet expired"));

		/*
		 * we simulate the renewal failure one more time. we know that leader = 'follower'
		 */
		int failAgain = output.getOut().length();
		failLeaderRenewal(follower, one, two);

		awaitUntil(10, 100,
				() -> output.getOut().substring(failAgain).contains("id : " + leader + " is the new leader"));
		awaitUntil(3, 100, () -> output.getOut()
			.substring(failAgain)
			.contains("Attempting to renew leader lease 'LeaseLock: " + "default - lease-lock (" + leader + ")'..."));
		awaitUntil(3, 100,
				() -> output.getOut()
					.substring(failAgain)
					.contains("Acquired lease 'LeaseLock: default - lease-lock (" + leader + ")'"));

		// proves that the canceled leader tries to acquire again the leadership
		awaitUntil(10, 100, () -> output.getOut()
			.substring(failAgain)
			.contains("Attempting to acquire leader lease 'LeaseLock: default - lease-lock (" + follower + ")'..."));
		awaitUntil(3, 100,
				() -> output.getOut()
					.substring(failAgain)
					.contains("Lock is held by " + leader + " and has not yet expired"));

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
		return new LeaseLock("default", LEASE_NAME, holderIdentity);
	}

	private Fabric8LeaderElectionCallbacks callbacks(String holderIdentity) {
		Fabric8LeaderElectionCallbacksAutoConfiguration configuration = new Fabric8LeaderElectionCallbacksAutoConfiguration();

		Runnable onStartLeadingCallback = configuration.onStartLeadingCallback(null, holderIdentity, PROPERTIES);
		Runnable onStopLeadingCallback = configuration.onStopLeadingCallback(null, holderIdentity, PROPERTIES);
		Consumer<String> onNewLeaderCallback = configuration.onNewLeaderCallback(null, PROPERTIES);

		return new Fabric8LeaderElectionCallbacks(onStartLeadingCallback, onStopLeadingCallback, onNewLeaderCallback);
	}

	private LeaderAndFollower leaderAndFollower(LeaderElectionConfig leaderElectionConfig,
			KubernetesClient kubernetesClient) {
		boolean oneIsLeader = leaderElectionConfig.getLock()
			.get(kubernetesClient)
			.getHolderIdentity()
			.equals(CANDIDATE_IDENTITY_ONE);

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
			one.leaderFeature().completeExceptionally(new RuntimeException("just because"));
		}
		else {
			two.leaderFeature().completeExceptionally(new RuntimeException("just because"));
		}
	}

	private record LeaderAndFollower(String leader, String follower) {

	}

}
