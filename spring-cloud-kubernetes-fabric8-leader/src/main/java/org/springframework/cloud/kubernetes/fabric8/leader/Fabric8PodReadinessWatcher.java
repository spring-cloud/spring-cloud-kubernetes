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

package org.springframework.cloud.kubernetes.fabric8.leader;

import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.leader.PodReadinessWatcher;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.guarded;

/**
 * @author Gytis Trikleris
 */
public class Fabric8PodReadinessWatcher implements PodReadinessWatcher, Watcher<Pod> {

	private static final LogAccessor LOGGER = new LogAccessor(LogFactory.getLog(Fabric8PodReadinessWatcher.class));

	private final ReentrantLock lock = new ReentrantLock();

	private final String podName;

	private final KubernetesClient kubernetesClient;

	private final Fabric8LeadershipController fabric8LeadershipController;

	private volatile boolean previousState;

	private volatile Watch watch;

	public Fabric8PodReadinessWatcher(String podName, KubernetesClient kubernetesClient,
			Fabric8LeadershipController fabric8LeadershipController) {
		this.podName = podName;
		this.kubernetesClient = kubernetesClient;
		this.fabric8LeadershipController = fabric8LeadershipController;
	}

	@Override
	public void start() {
		if (watch == null) {
			guarded(lock, () -> {
				if (watch == null) {
					LOGGER.debug(() -> "Starting pod readiness watcher for :" + podName);
					PodResource podResource = kubernetesClient.pods().withName(podName);
					previousState = podResource.isReady();
					watch = podResource.watch(this);
				}
			});
		}
	}

	@Override
	public void stop() {
		if (watch != null) {
			guarded(lock, () -> {
				if (watch != null) {
					LOGGER.debug(() -> "Stopping pod readiness watcher for :" + podName);
					watch.close();
					watch = null;
				}
			});
		}
	}

	@Override
	public void eventReceived(Action action, Pod pod) {
		boolean currentState = Readiness.isPodReady(pod);
		if (previousState != currentState) {
			guarded(lock, () -> {
				if (previousState != currentState) {
					LOGGER.debug(() -> "readiness status changed for pod : " + podName + " to state: " + currentState
							+ ", triggering leadership update");
					previousState = currentState;
					fabric8LeadershipController.update();
				}
			});
		}
	}

	@Override
	public void onClose(WatcherException cause) {
		if (cause != null) {
			guarded(lock, () -> {
				LOGGER.warn(() -> "Watcher stopped unexpectedly, will restart" + cause.getMessage());
				watch = null;
				start();
			});
		}
	}

}
