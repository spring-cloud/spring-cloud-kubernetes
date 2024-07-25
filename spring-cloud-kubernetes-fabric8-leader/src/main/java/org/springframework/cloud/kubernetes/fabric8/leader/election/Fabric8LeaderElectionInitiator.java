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

	private final AtomicReference<CompletableFuture<?>> leaderFuture = new AtomicReference<>();

	Fabric8LeaderElectionInitiator(String holderIdentity, String podNamespace, KubernetesClient fabric8KubernetesClient,
			LeaderElectionConfig leaderElectionConfig, LeaderElectionProperties leaderElectionProperties) {
		this.holderIdentity = holderIdentity;
		this.podNamespace = podNamespace;
		this.fabric8KubernetesClient = fabric8KubernetesClient;
		this.leaderElectionConfig = leaderElectionConfig;
		this.leaderElectionProperties = leaderElectionProperties;
	}

	@PostConstruct
	void postConstruct() {
		LOG.info(() -> "starting leader initiator");
		executorService.set(Executors.newSingleThreadExecutor());
		CompletableFuture<Void> podReadyFuture = new CompletableFuture<>();

		// wait until pod is ready
		if (leaderElectionProperties.waitForPodReady()) {
			LOG.info(() -> "need to wait until pod is ready");
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
					LOG.error(() -> "leader election was not OK");
					throw new RuntimeException(e);
				}

			}, 1, 1, TimeUnit.SECONDS));
		}

		// wait in a different thread until the pod is ready
		// and in the same thread start the leader election
		executorService.get().submit(() -> {
			try {
				if (leaderElectionProperties.waitForPodReady()) {
					CompletableFuture<?> ready = podReadyFuture
						.whenComplete((x, y) -> scheduledFuture.get().cancel(true));
					ready.get();
				}
				leaderFuture.set(leaderElector(leaderElectionConfig, fabric8KubernetesClient).start());
				leaderFuture.get();
			}
			catch (Exception e) {
				if (e instanceof CancellationException) {
					LOG.warn(() -> "leaderFuture was canceled");
				}
				throw new RuntimeException(e);
			}
		});

	}

	@PreDestroy
	void preDestroy() {
		LOG.info(() -> "preDestroy called in the leader initiator");
		if (scheduledFuture.get() != null) {
			// if the task is not running, this has no effect
			// if the task is running, calling this will also make sure
			// that the caching executor will shut down too.
			scheduledFuture.get().cancel(true);
		}

		if (leaderFuture.get() != null) {
			LOG.info(() -> "leader will be canceled");
			// needed to release the lock, fabric8 internally expects this one to be
			// called
			leaderFuture.get().cancel(true);
		}
		executorService.get().shutdownNow();

	}

	// can't be a bean because on context refresh, we keep starting an instance, and
	// it is not allowed to start the same instance twice.
	LeaderElector leaderElector(LeaderElectionConfig config, KubernetesClient fabric8KubernetesClient) {
		return fabric8KubernetesClient.leaderElector().withConfig(config).build();
	}

}
