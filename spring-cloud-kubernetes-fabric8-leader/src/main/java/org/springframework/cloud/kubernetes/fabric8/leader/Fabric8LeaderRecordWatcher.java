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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;

import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeaderRecordWatcher;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.guarded;

/**
 * @author Gytis Trikleris
 */
public class Fabric8LeaderRecordWatcher implements LeaderRecordWatcher, Watcher<ConfigMap> {

	private static final LogAccessor LOGGER = new LogAccessor(Fabric8LeaderRecordWatcher.class);

	private final ReentrantLock lock = new ReentrantLock();

	private final Fabric8LeadershipController fabric8LeadershipController;

	private final LeaderProperties leaderProperties;

	private final KubernetesClient kubernetesClient;

	private volatile Watch configMapWatch;

	public Fabric8LeaderRecordWatcher(LeaderProperties leaderProperties,
			Fabric8LeadershipController fabric8LeadershipController, KubernetesClient kubernetesClient) {
		this.fabric8LeadershipController = fabric8LeadershipController;
		this.leaderProperties = leaderProperties;
		this.kubernetesClient = kubernetesClient;
	}

	public void start() {
		if (configMapWatch == null) {
			guarded(lock, () -> {
				if (configMapWatch == null) {
					LOGGER.debug(() -> "Starting leader record watcher");
					configMapWatch = kubernetesClient.configMaps()
						.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
						.withName(leaderProperties.getConfigMapName())
						.watch(this);
				}
			});
		}
	}

	public void stop() {
		if (configMapWatch != null) {
			guarded(lock, () -> {
				if (configMapWatch != null) {
					LOGGER.debug(() -> "Stopping leader record watcher");
					configMapWatch.close();
					configMapWatch = null;
				}
			});
		}
	}

	@Override
	public void eventReceived(Action action, ConfigMap configMap) {
		LOGGER.debug(() -> action + " event received, triggering leadership update");

		if (!Action.ERROR.equals(action)) {
			fabric8LeadershipController.update();
		}
	}

	@Override
	public void onClose(WatcherException cause) {
		if (cause != null) {
			guarded(lock, () -> {
				LOGGER.warn(() -> "Watcher stopped unexpectedly, will restart because of : " + cause);
				configMapWatch = null;
				start();
			});
		}
	}

}
