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

package org.springframework.cloud.kubernetes.leader;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gytis Trikleris
 */
public class LeaderRecordWatcher implements Watcher<ConfigMap> {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeaderRecordWatcher.class);

	private final Object lock = new Object();

	private final LeadershipController leadershipController;

	private final LeaderProperties leaderProperties;

	private final KubernetesClient kubernetesClient;

	private Watch watch;

	public LeaderRecordWatcher(LeaderProperties leaderProperties, LeadershipController leadershipController,
			KubernetesClient kubernetesClient) {
		this.leadershipController = leadershipController;
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
			this.leadershipController.update();
		}
	}

	@Override
	public void onClose(KubernetesClientException cause) {
		if (cause != null) {
			synchronized (this.lock) {
				LOGGER.warn("Watcher stopped unexpectedly, will restart", cause);
				this.watch = null;
				start();
			}
		}
	}

}
