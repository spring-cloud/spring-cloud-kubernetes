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
import java.time.Duration;
import java.util.function.Consumer;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.Lock;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;
import io.kubernetes.client.util.Config;
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

@ExtendWith(OutputCaptureExtension.class)
class K8sClientLeaderElectionConcurrentIT {

	private static final String LEASE_NAME = "lease-lock";

	private static final LeaderElectionProperties PROPERTIES = new LeaderElectionProperties(false, false,
			Duration.ofSeconds(15), "default", LEASE_NAME, Duration.ofSeconds(5), Duration.ofSeconds(2),
			Duration.ofSeconds(5), false, true);

	private static final String CANDIDATE_IDENTITY_ONE = "one";

	private static final String CANDIDATE_IDENTITY_TWO = "two";

	private KubernetesClientLeaderElectionInitiator one;

	private KubernetesClientLeaderElectionInitiator two;

	private static ApiClient apiClient;

	@BeforeAll
	static void beforeAll() {

		K3sContainer container = Commons.container();
		container.start();

		String kubeConfigYaml = container.getKubeConfigYaml();

		try {
			apiClient = Config.fromConfig(new StringReader(kubeConfigYaml));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	@AfterAll
	static void afterAll() {

		CoordinationV1Api api = new CoordinationV1Api(apiClient);

		try {
			api.deleteNamespacedLease(LEASE_NAME, "default").execute();
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}

	}

	@AfterEach
	void afterEach() {
		one.preDestroy();
		two.preDestroy();
	}

	@Test
	void test(CapturedOutput output) {

		LeaderElectionConfig leaderElectionConfigOne = leaderElectionConfig(CANDIDATE_IDENTITY_ONE);
		KubernetesClientLeaderElectionCallbacks callbacksOne = callbacks(CANDIDATE_IDENTITY_ONE);
		one = new KubernetesClientLeaderElectionInitiator(CANDIDATE_IDENTITY_ONE, "default", leaderElectionConfigOne,
				PROPERTIES, () -> true, callbacksOne);

		LeaderElectionConfig leaderElectionConfigTwo = leaderElectionConfig(CANDIDATE_IDENTITY_TWO);
		KubernetesClientLeaderElectionCallbacks callbacksTwo = callbacks(CANDIDATE_IDENTITY_TWO);
		two = new KubernetesClientLeaderElectionInitiator(CANDIDATE_IDENTITY_TWO, "default", leaderElectionConfigTwo,
				PROPERTIES, () -> true, callbacksTwo);

		one.postConstruct();
		two.postConstruct();

		// both try to acquire the lock
		awaitUntil(5, 100, () -> output.getOut().contains("starting leader initiator : one"));
		awaitUntil(5, 100, () -> output.getOut().contains("starting leader initiator : two"));

		// someone has become the leader
		awaitUntil(5, 100, () -> output.getOut().contains("LeaderElection lock is currently held by"));

		LeaderAndFollower leaderAndFollower = leaderAndFollower(leaderElectionConfigOne);
		String leader = leaderAndFollower.leader();
		String follower = leaderAndFollower.follower();

		// someone has become the leader
		awaitUntil(5, 100, () -> output.getOut().contains("LeaderElection lock is currently held by " + leader));
		awaitUntil(3, 100, () -> output.getOut().contains("id : " + leader + " is the new leader"));

		// the other elector says it can't acquire the lock
		awaitUntil(10, 100, () -> output.getOut().contains("Lock is held by " + leader + " and has not yet expired"));
		awaitUntil(10, 100, () -> output.getOut().contains("The tryAcquireOrRenew result is false"));

		int beforeRelease = output.getOut().length();
		failLeaderRenewal(leader, one, two);

		/*
		 * we simulated above that renewal failed and leader future was canceled. In this
		 * case, the 'notLeader' picks up the leadership, the 'leader' is now a
		 * "follower", it re-tries to take leadership.
		 */
		awaitUntil(10, 100,
				() -> output.getOut().substring(beforeRelease).contains("id : " + follower + " is the new leader"));
		awaitUntil(10, 100,
				() -> output.getOut().substring(beforeRelease).contains("Failed to renew lease, lose leadership"));

		// the other candidate still tries to become leader
		awaitUntil(10, 100,
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
		// the other candidate still tries to become leader
		awaitUntil(10, 100,
				() -> output.getOut()
					.substring(beforeRelease)
					.contains("Lock is held by " + leader + " and has not yet expired"));
	}

	private LeaderElectionConfig leaderElectionConfig(String holderIdentity) {

		Lock lock = leaseLock(holderIdentity);

		LeaderElectionConfig leaderElectionConfig = new LeaderElectionConfig();
		leaderElectionConfig.setLock(lock);
		leaderElectionConfig.setLeaseDuration(PROPERTIES.leaseDuration());
		leaderElectionConfig.setRenewDeadline(PROPERTIES.renewDeadline());
		leaderElectionConfig.setRetryPeriod(PROPERTIES.retryPeriod());

		return leaderElectionConfig;
	}

	private LeaseLock leaseLock(String holderIdentity) {
		return new LeaseLock("default", LEASE_NAME, holderIdentity, apiClient);
	}

	private KubernetesClientLeaderElectionCallbacks callbacks(String holderIdentity) {
		KubernetesClientLeaderElectionCallbacksAutoConfiguration configuration = new KubernetesClientLeaderElectionCallbacksAutoConfiguration();

		Runnable onStartLeadingCallback = configuration.onStartLeadingCallback(null, holderIdentity, PROPERTIES);
		Runnable onStopLeadingCallback = configuration.onStopLeadingCallback(null, holderIdentity, PROPERTIES);
		Consumer<String> onNewLeaderCallback = configuration.onNewLeaderCallback(null, PROPERTIES);

		return new KubernetesClientLeaderElectionCallbacks(onStartLeadingCallback, onStopLeadingCallback,
				onNewLeaderCallback);
	}

	private LeaderAndFollower leaderAndFollower(LeaderElectionConfig leaderElectionConfig) {

		boolean oneIsLeader;
		try {
			oneIsLeader = leaderElectionConfig.getLock().get().getHolderIdentity().equals(CANDIDATE_IDENTITY_ONE);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}

		if (oneIsLeader) {
			return new LeaderAndFollower("one", "two");
		}
		else {
			return new LeaderAndFollower("two", "one");
		}
	}

	private void failLeaderRenewal(String currentLeader, KubernetesClientLeaderElectionInitiator one,
			KubernetesClientLeaderElectionInitiator two) {
		if (currentLeader.equals("one")) {
			one.leaderElector().close();
		}
		else {
			two.leaderElector().close();
		}
	}

	private record LeaderAndFollower(String leader, String follower) {

	}

}
