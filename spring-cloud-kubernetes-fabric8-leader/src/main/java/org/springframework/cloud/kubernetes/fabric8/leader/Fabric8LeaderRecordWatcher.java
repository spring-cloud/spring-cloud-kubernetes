/*
 * Copyright 2013-2019 the original author or authors.
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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;

/**
 * @author Gytis Trikleris
 */
public class Fabric8LeaderRecordWatcher
		implements org.springframework.cloud.kubernetes.commons.leader.LeaderRecordWatcher, Watcher<ConfigMap> {

	private static final Logger LOGGER = LoggerFactory.getLogger(Fabric8LeaderRecordWatcher.class);

	private final Object lock = new Object();

	private final Fabric8LeadershipController fabric8LeadershipController;

	private final LeaderProperties leaderProperties;

	private final KubernetesClient kubernetesClient;

	private Watch watch;

	public Fabric8LeaderRecordWatcher(LeaderProperties leaderProperties,
			Fabric8LeadershipController fabric8LeadershipController, KubernetesClient kubernetesClient) {
		this.fabric8LeadershipController = fabric8LeadershipController;
		this.leaderProperties = leaderProperties;
		this.kubernetesClient = kubernetesClient;
	}

	public void start() {
		if (this.watch == null) {
			synchronized (this.lock) {
				if (this.watch == null) {
					LOGGER.debug("Starting leader record watcher");
					this.watch = this.kubernetesClient.configMaps()
							.inNamespace(this.leaderProperties.getNamespace(this.kubernetesClient.getNamespace()))
							.withName(this.leaderProperties.getConfigMapName()).watch(this);
				}
			}
		}
	}

	public void stop() {
		if (this.watch != null) {
			synchronized (this.lock) {
				if (this.watch != null) {
					LOGGER.debug("Stopping leader record watcher");
					this.watch.close();
					this.watch = null;
				}
			}
		}
	}

	@Override
	public void eventReceived(Action action, ConfigMap configMap) {
		LOGGER.debug("'{}' event received, triggering leadership update", action);

		if (!Action.ERROR.equals(action)) {
			this.fabric8LeadershipController.update();
		}
	}

	@Override
	public void onClose(WatcherException cause) {
		if (cause != null) {
			synchronized (this.lock) {
				LOGGER.warn("Watcher stopped unexpectedly, will restart", cause);
				this.watch = null;
				start();
			}
		}
	}

}
