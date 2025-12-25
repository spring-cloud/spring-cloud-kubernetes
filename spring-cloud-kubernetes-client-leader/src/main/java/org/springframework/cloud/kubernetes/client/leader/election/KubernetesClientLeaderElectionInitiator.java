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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.cloud.kubernetes.commons.leader.election.PodReadyRunner;
import org.springframework.core.log.LogAccessor;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionInitiatorUtil.attachReadinessLoggerPipeline;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionInitiatorUtil.blockReadinessCheck;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionInitiatorUtil.shutDownExecutor;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionInitiatorUtil.sleep;

/**
 * @author wind57
 */
final class KubernetesClientLeaderElectionInitiator {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientLeaderElectionInitiator.class);

	private final PodReadyRunner podReadyRunner;

	private final String candidateIdentity;

	private final LeaderElectionConfig leaderElectionConfig;

	private final LeaderElectionProperties leaderElectionProperties;

	private final boolean waitForPodReady;

	private final ExecutorService podReadyWaitingExecutor;

	private final BooleanSupplier podReadySupplier;

	private final KubernetesClientLeaderElectionCallbacks callbacks;

	private volatile LeaderElector leaderElector;

	private volatile CompletableFuture<Void> podReadyFuture;

	KubernetesClientLeaderElectionInitiator(String candidateIdentity, String candidateNamespace,
			LeaderElectionConfig leaderElectionConfig, LeaderElectionProperties leaderElectionProperties,
			BooleanSupplier podReadySupplier, KubernetesClientLeaderElectionCallbacks callbacks) {
		this.candidateIdentity = candidateIdentity;
		this.leaderElectionConfig = leaderElectionConfig;
		this.leaderElectionProperties = leaderElectionProperties;
		this.waitForPodReady = leaderElectionProperties.waitForPodReady();
		this.podReadySupplier = podReadySupplier;
		this.callbacks = callbacks;

		this.podReadyWaitingExecutor = newSingleThreadExecutor(
				runnable -> new Thread(runnable, "Fabric8LeaderElectionInitiator-" + candidateIdentity));

		this.podReadyRunner = new PodReadyRunner(candidateIdentity, candidateNamespace);
	}

	/**
	 * <pre>
	 * 	We first try to see if we need to wait for the pod to be ready
	 * 	before starting the leader election process.
	 * </pre>
	 *
	 */
	@PostConstruct
	void postConstruct() {

		LOG.info(() -> "starting leader initiator : " + candidateIdentity);

		// wait until the pod is ready
		if (waitForPodReady) {
			LOG.info(() -> "will wait until pod " + candidateIdentity + " is ready");
			podReadyFuture = podReadyRunner.podReady(podReadySupplier);
		}
		else {
			podReadyFuture = CompletableFuture.completedFuture(null);
		}

		// wait in a different thread until the pod is ready
		// and don't block the main application from starting
		podReadyWaitingExecutor.execute(() -> {
			try {
				if (waitForPodReady) {
					CompletableFuture<?> ready = attachReadinessLoggerPipeline(podReadyFuture, candidateIdentity);
					blockReadinessCheck(ready);
					startLeaderElection();
				}
				else {
					startLeaderElection();
				}
			}
			catch (Exception e) {
				LOG.error(e, () -> "failure : " + e.getMessage());
			}
		});

	}

	@PreDestroy
	void preDestroy() {
		LOG.info(() -> "preDestroy called on the leader initiator : " + candidateIdentity);

		if (podReadyFuture != null && !podReadyFuture.isDone()) {
			// if the task is not running, this has no effect.
			// if the task is running, calling this will also make sure
			// that the caching executor will shut down too.
			LOG.debug(() -> "podReadyFuture will be canceled for : " + candidateIdentity);
			podReadyFuture.cancel(true);
		}

		if (!podReadyWaitingExecutor.isShutdown()) {
			shutDownExecutor(podReadyWaitingExecutor, candidateIdentity);
		}

		if (leaderElector != null) {
			leaderElector.close();
		}
	}

	private void startLeaderElection() {

		boolean failedDuringStartup = false;
		leaderElector = new LeaderElector(leaderElectionConfig);
		try {
			// this runs in a while(true) loop and every throwable is just logged,
			// it does not spill over to our code
			leaderElector.run(callbacks.onStartLeadingCallback(), callbacks.onStopLeadingCallback(),
					callbacks.onNewLeaderCallback());
		}
		catch (Exception e) {
			// this is only possible when we can't start leader election, not during
			// its inner workings
			LOG.error(e, () -> "failure starting leader election: " + e.getMessage());
			failedDuringStartup = true;
		}
		finally {
			leaderElector.close();
		}

		if (!failedDuringStartup) {
			// as soon as leader election is over, re-start it
			sleep(leaderElectionProperties);
			podReadyWaitingExecutor.execute(this::startLeaderElection);
		}

	}

}
