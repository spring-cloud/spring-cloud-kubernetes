/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.leader;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gytis Trikleris
 */
public class PodReadinessWatcher implements Watcher<Pod> {

	private static final Logger LOGGER = LoggerFactory.getLogger(PodReadinessWatcher.class);

	private final String podName;

	private final KubernetesClient kubernetesClient;

	private final LeadershipController leadershipController;

	private boolean previousState;

	private Watch watch;

	public PodReadinessWatcher(String podName, KubernetesClient kubernetesClient,
		LeadershipController leadershipController) {
		this.podName = podName;
		this.kubernetesClient = kubernetesClient;
		this.leadershipController = leadershipController;
	}

	public void start() {
		if (watch == null) {
			LOGGER.debug("Starting pod readiness watcher for '{}'", podName);
			PodResource<Pod, DoneablePod> podResource = kubernetesClient
				.pods()
				.withName(podName);
			previousState = podResource.isReady();
			watch = podResource.watch(this);
		}
	}

	public void stop() {
		if (watch != null) {
			LOGGER.debug("Stopping pod readiness watcher for '{}'", podName);
			watch.close();
			watch = null;
		}
	}

	@Override
	public void eventReceived(Action action, Pod pod) {
		boolean currentState = Readiness.isPodReady(pod);
		if (previousState != currentState) {
			LOGGER.debug("'{}' readiness status changed to '{}', triggering leadership update", podName, currentState);
			previousState = currentState;
			leadershipController.update();
		}
	}

	@Override
	public void onClose(KubernetesClientException cause) {
		if (cause != null) {
			LOGGER.warn("Watcher stopped unexpectedly, will restart", cause);
			watch = null;
			start();
		}
	}
}
