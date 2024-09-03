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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.CachedSingleThreadScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
final class Fabric8LeaderElectionInitiator {

	private static final LogAccessor LOG = new LogAccessor(Fabric8LeaderElectionInitiator.class);

	private final CachedSingleThreadScheduler scheduler = new CachedSingleThreadScheduler();

	private final String holderIdentity;

	private final String podNamespace;

	private final KubernetesClient fabric8KubernetesClient;

	private final LeaderElectionConfig leaderElectionConfig;

	private final LeaderElectionProperties leaderElectionProperties;

	private final AtomicReference<ExecutorService> executorService = new AtomicReference<>();

	private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

	private final AtomicReference<CompletableFuture<?>> leaderFutureReference = new AtomicReference<>();

	// not private for testing
	final AtomicBoolean destroyCalled = new AtomicBoolean(false);

	Fabric8LeaderElectionInitiator(String holderIdentity, String podNamespace, KubernetesClient fabric8KubernetesClient,
			LeaderElectionConfig leaderElectionConfig, LeaderElectionProperties leaderElectionProperties) {
		this.holderIdentity = holderIdentity;
		this.podNamespace = podNamespace;
		this.fabric8KubernetesClient = fabric8KubernetesClient;
		this.leaderElectionConfig = leaderElectionConfig;
		this.leaderElectionProperties = leaderElectionProperties;
	}

	/**
	 *  in a CachedSingleThreadScheduler start pod readiness and keep it running 'forever',
	 *  until it is successful or failed. That is run in a daemon thread.
	 *
	 *  In a different pool ('executorService'), block until the above CompletableFuture is done.
	 *  Only when it's done, start the leader election process.
	 *  If pod readiness fails, leader election is not started.
	 *
 	 */
	@PostConstruct
	void postConstruct() {
		LOG.info(() -> "starting leader initiator : " + holderIdentity);
		executorService.set(Executors.newSingleThreadExecutor(
			r -> new Thread(r, "Fabric8LeaderElectionInitiator-" + holderIdentity)));
		CompletableFuture<Void> podReadyFuture = new CompletableFuture<>();

		// wait until pod is ready
		if (leaderElectionProperties.waitForPodReady()) {
			LOG.info(() -> "need to wait until pod is ready : " + holderIdentity);
			scheduledFuture.set(scheduler.scheduleWithFixedDelay(() -> {

				try {
					LOG.info(() -> "waiting for pod : " + holderIdentity + " in namespace : " + podNamespace
							+ " to be ready");
					Pod pod = fabric8KubernetesClient.pods().inNamespace(podNamespace).withName(holderIdentity).get();
					boolean podReady = Readiness.isPodReady(pod);
					if (podReady) {
						LOG.info(() -> "Pod : " + holderIdentity + " in namespace : " + podNamespace + " is ready");
						podReadyFuture.complete(null);
					}
					else {
						LOG.info(() -> "Pod : " + holderIdentity + " in namespace : " + podNamespace + " is not ready, "
								+ "will retry in one second");
					}
				}
				catch (Exception e) {
					LOG.error(() -> "exception waiting for pod : " + e.getMessage());
					LOG.error(() -> "leader election for " + holderIdentity + "  was not successful");
					podReadyFuture.completeExceptionally(e);
					throw new RuntimeException(e);
				}

			}, 1, 1, TimeUnit.SECONDS));
		}

		// wait in a different thread until the pod is ready
		// and in the same thread start the leader election
		executorService.get().submit(() -> {
			if (leaderElectionProperties.waitForPodReady()) {
				CompletableFuture<?> ready = podReadyFuture
					.whenComplete((ok, error) -> {
						if (error != null) {
							LOG.error(() -> "readiness failed for : " + holderIdentity);
							LOG.error(() -> "leader election for : " + holderIdentity + " will not start");
							scheduledFuture.get().cancel(true);
						}
						else {
							LOG.info(() -> holderIdentity + " is ready");
							scheduledFuture.get().cancel(true);
						}
					});
				try {
					ready.get();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}

				// readiness check passed, start leader election
				if (!podReadyFuture.isCompletedExceptionally()) {
					startLeaderElection();
				}

			}
			else {
				startLeaderElection();
			}
		});

	}

	@PreDestroy
	void preDestroy() {
		destroyCalled();
		LOG.info(() -> "preDestroy called in the leader initiator : " + holderIdentity);
		if (scheduledFuture.get() != null) {
			// if the task is not running, this has no effect
			// if the task is running, calling this will also make sure
			// that the caching executor will shut down too.
			scheduledFuture.get().cancel(true);
		}

		if (leaderFutureReference.get() != null) {
			LOG.info(() -> "leader will be canceled : " + holderIdentity);
			// needed to release the lock, fabric8 internally expects this one to be
			// called
			leaderFutureReference.get().cancel(true);
		}
		shutDownExecutor();
	}

	void destroyCalled() {
		destroyCalled.set(true);
	}

	void shutDownExecutor() {
		executorService.get().shutdownNow();
	}

	private void startLeaderElection() {
		try {
			CompletableFuture<?> leaderFuture = leaderElector(leaderElectionConfig, fabric8KubernetesClient).start();
			leaderFuture.whenCompleteAsync((ok, error) -> {

				if (ok != null) {
					LOG.info(() -> "leaderFuture finished normally, will re-start it for  : " + holderIdentity);
					startLeaderElection();
					return;
				}

				if (error instanceof CancellationException) {
					if (!destroyCalled.get()) {
						LOG.warn(() -> "renewal failed for  : " + holderIdentity + ", will re-start it after : " +
							leaderElectionProperties.waitAfterRenewalFailure() + " seconds");
						try {
							TimeUnit.SECONDS.sleep(leaderElectionProperties.waitAfterRenewalFailure());
						}
						catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
						startLeaderElection();
					}
				}
			}, executorService.get());
			leaderFutureReference.set(leaderFuture);
			leaderFutureReference.get().get();
		}
		catch (Exception e) {
			if (e instanceof CancellationException) {
				LOG.warn(() -> "leaderFuture was canceled for : " + holderIdentity);
			}
			throw new RuntimeException(e);
		}
	}

	private LeaderElector leaderElector(LeaderElectionConfig config, KubernetesClient fabric8KubernetesClient) {
		return fabric8KubernetesClient.leaderElector().withConfig(config).build();
	}

}
