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

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.commons.leader.election.events.NewLeaderEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.leader.election.Assertions.assertAcquireAndRenew;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
@TestPropertySource(
		properties = { "spring.cloud.kubernetes.leader.election.wait-for-pod-ready=true", "readiness.passes=true" })
@ContextConfiguration(classes = { Fabric8LeaderElectionEventsPublishedIT.LeaderElectionEventsListenerConfig.class })
class Fabric8LeaderElectionEventsPublishedIT extends AbstractLeaderElection {

	private static final LogAccessor LOG = new LogAccessor(Fabric8LeaderElectionEventsPublishedIT.class);

	private static final String NAME = "events-are-published-it";

	@Autowired
	private Fabric8LeaderElectionInitiator initiator;

	@BeforeAll
	static void beforeAll() {
		AbstractLeaderElection.beforeAll(NAME);
	}

	@AfterEach
	void afterEach() {
		stopFutureAndDeleteLease(initiator);
	}

	@Test
	void test(CapturedOutput output) {

		assertAcquireAndRenew(output, this::getLease, NAME);

		// 1. we have a new leader, NewLeaderEvent is published
		awaitUntil(10, 20, () -> output.getOut().contains("New leader event received for : events-are-published-it"));

		// 2. we have a new leader, StartLeadingEventListener is published
		awaitUntil(10, 20,
				() -> output.getOut().contains("Start leading event received for : events-are-published-it"));

		int firstLeaderIndex = output.getOut().indexOf("Start leading event received for : events-are-published-it");

		// simulate that leadership has changed
		Lease lease = getLease();
		lease.getSpec().setHolderIdentity("leader-lost-then-recovers-it-is-not-the-leader-anymore");
		kubernetesClient.leases().inNamespace("default").resource(lease).update();

		// 3. 'events-are-published-it' gave up its leadership
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(firstLeaderIndex)
					.contains("Stop leading event received for : events-are-published-it"));

		// 4. 'leader-lost-then-recovers-it-is-not-the-leader-anymore' acquired it
		awaitUntil(10, 20, () -> output.getOut()
			.substring(firstLeaderIndex)
			.contains("New leader event received for : leader-lost-then-recovers-it-is-not-the-leader-anymore"));

		int secondLeaderIndex = output.getOut()
			.indexOf("New leader event received for : leader-lost-then-recovers-it-is-not-the-leader-anymore");

		lockExpiresLeadershipChanges(output, secondLeaderIndex);

		// 10. we gain the leadership back
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(secondLeaderIndex)
					.contains("New leader event received for : events-are-published-it"));

		// 11. we gain the leadership back
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(secondLeaderIndex)
					.contains("Start leading event received for : events-are-published-it"));

	}

	private void lockExpiresLeadershipChanges(CapturedOutput output, int index) {

		// 5. once we start leadership again, we try to acquire the new lock
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(index)
					.contains("Attempting to acquire leader lease 'LeaseLock: "
							+ "default - spring-k8s-leader-election-lock (" + NAME + ")"));

		// 6. we can not acquire the new lock, since it did not yet expire
		// (the new leader is not going to renew it since it's an artificial leader)
		awaitUntil(10, 20,
				() -> output.getOut()
					.substring(index)
					.contains("Failed to acquire lease 'LeaseLock: " + "default - spring-k8s-leader-election-lock ("
							+ NAME + ")' retrying..."));

		// 7. leader is again us, since lock expires
		awaitUntil(10, 500,
				() -> output.getOut()
					.substring(index)
					.contains("Leader changed from leader-lost-then-recovers-it-is-not-the-leader-anymore to " + NAME));

		// 8. callback is again triggered
		awaitUntil(10, 500, () -> output.getOut().substring(index).contains("id : " + NAME + " is the new leader"));

		// 9. the other callback is triggered also
		awaitUntil(10, 500, () -> output.getOut().substring(index).contains(NAME + " is now a leader"));
	}

	static class NewLeaderEventListener implements ApplicationListener<@NonNull NewLeaderEvent> {

		@Override
		public void onApplicationEvent(NewLeaderEvent newLeaderEvent) {
			LOG.info(() -> "New leader event received for : " + newLeaderEvent.candidateIdentity());
		}

	}

	static class StartLeadingEventListener implements ApplicationListener<@NonNull StartLeadingEvent> {

		@Override
		public void onApplicationEvent(StartLeadingEvent startLeadingEvent) {
			LOG.info(() -> "Start leading event received for : " + startLeadingEvent.candidateIdentity());
		}

	}

	static class StopLeadingEventListener implements ApplicationListener<@NonNull StopLeadingEvent> {

		@Override
		public void onApplicationEvent(StopLeadingEvent stopLeadingEvent) {
			LOG.info(() -> "Stop leading event received for : " + stopLeadingEvent.candidateIdentity());
		}

	}

	@TestConfiguration
	static class LeaderElectionEventsListenerConfig {

		@Bean
		NewLeaderEventListener newLeaderEventListener() {
			return new NewLeaderEventListener();
		}

		@Bean
		StartLeadingEventListener startLeadingEventListener() {
			return new StartLeadingEventListener();
		}

		@Bean
		StopLeadingEventListener stopLeadingEventListener() {
			return new StopLeadingEventListener();
		}

	}

}
