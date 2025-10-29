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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.cloud.kubernetes.commons.leader.election.PodReadyRunner;
import org.springframework.core.log.LogAccessor;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * @author wind57
 */
final class Fabric8LeaderElectionInitiator {

	private static final LogAccessor LOG = new LogAccessor(Fabric8LeaderElectionInitiator.class);

	private final PodReadyRunner podReadyRunner;

	private final String candidateIdentity;

	private final KubernetesClient fabric8KubernetesClient;

	private final LeaderElectionConfig leaderElectionConfig;

	private final LeaderElectionProperties leaderElectionProperties;

	private final boolean waitForPodReady;

	private final ExecutorService podReadyWaitingExecutor;

	private final BooleanSupplier podReadySupplier;

	private volatile CompletableFuture<Void> podReadyFuture;

	private volatile boolean destroyCalled = false;

	private volatile CompletableFuture<?> leaderFuture;

	Fabric8LeaderElectionInitiator(String candidateIdentity, String candidateNamespace,
			KubernetesClient fabric8KubernetesClient, LeaderElectionConfig leaderElectionConfig,
			LeaderElectionProperties leaderElectionProperties, BooleanSupplier podReadySupplier) {
		this.candidateIdentity = candidateIdentity;
		this.fabric8KubernetesClient = fabric8KubernetesClient;
		this.leaderElectionConfig = leaderElectionConfig;
		this.leaderElectionProperties = leaderElectionProperties;
		this.waitForPodReady = leaderElectionProperties.waitForPodReady();
		this.podReadySupplier = podReadySupplier;

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
		podReadyWaitingExecutor.submit(() -> {
			if (waitForPodReady) {

				// if 'ready' is already completed at this point, thread will run this,
				// otherwise it will attach the pipeline and move on to
				// 'blockReadinessCheck'
				CompletableFuture<?> ready = podReadyFuture.whenComplete((ok, error) -> {
					if (error != null) {
						LOG.error(() -> "readiness failed for : " + candidateIdentity +
							", leader election will not start");
					}
					else {
						LOG.info(() -> candidateIdentity + " is ready");
						startLeaderElection();
					}
				});

				blockReadinessCheck(ready);

			}
			else {
				startLeaderElection();
			}
		});

	}

	@PreDestroy
	void preDestroy() {
		destroyCalled = true;
		LOG.info(() -> "preDestroy called on the leader initiator : " + candidateIdentity);

		if (podReadyFuture != null && !podReadyFuture.isDone()) {
			// if the task is not running, this has no effect.
			// if the task is running, calling this will also make sure
			// that the caching executor will shut down too.
			LOG.debug(() -> "podReadyFuture will be canceled for : " + candidateIdentity);
			podReadyFuture.cancel(true);
		}

		if (leaderFuture != null) {
			LOG.info(() -> "leaderFuture will be canceled for : " + candidateIdentity);
			// needed to release the lock, in case we are holding it.
			// fabric8 internally expects this one to be called
			LOG.debug(() -> "leaderFuture will be canceled for : " + candidateIdentity);
			leaderFuture.cancel(true);
		}
		if (!podReadyWaitingExecutor.isShutdown()) {
			LOG.debug(() -> "podReadyWaitingExecutor will be shutdown for : " + candidateIdentity);
			podReadyWaitingExecutor.shutdownNow();
		}
	}

	private void startLeaderElection() {
		leaderFuture = leaderElector(leaderElectionConfig, fabric8KubernetesClient).start();
		leaderFuture.whenComplete((ok, error) -> {

			if (ok != null) {
				LOG.info(() -> "leaderFuture finished normally, will re-start it for  : " + candidateIdentity);
				startLeaderElection();
				return;
			}

			if (error instanceof CancellationException) {
				if (!destroyCalled) {
					LOG.warn(() -> "renewal failed for  : " + candidateIdentity + ", will re-start it after : "
							+ leaderElectionProperties.waitAfterRenewalFailure().toSeconds() + " seconds");
					sleep();
					startLeaderElection();
				}
			}
			else {
				LOG.warn(() -> "leader election is over for : " + candidateIdentity);
			}

			try {
				leaderFuture.get();
			}
			catch (Exception e) {
				LOG.warn(() -> "leader election failed for : " + candidateIdentity + ". Trying to recover...");
			}
		});
	}

	private LeaderElector leaderElector(LeaderElectionConfig config, KubernetesClient fabric8KubernetesClient) {
		return fabric8KubernetesClient.leaderElector().withConfig(config).build();
	}

	private void sleep() {
		try {
			TimeUnit.SECONDS.sleep(leaderElectionProperties.waitAfterRenewalFailure().toSeconds());
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void blockReadinessCheck(CompletableFuture<?> ready) {
		try {
			ready.get();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
