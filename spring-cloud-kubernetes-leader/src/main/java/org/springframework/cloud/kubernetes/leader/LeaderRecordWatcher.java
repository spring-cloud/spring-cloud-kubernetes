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
		if (watch == null) {
			LOGGER.debug("Starting leader record watcher");
			watch = kubernetesClient
				.configMaps()
				.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
				.withName(leaderProperties.getConfigMapName())
				.watch(this);
		}
	}

	public void stop() {
		if (watch != null) {
			LOGGER.debug("Stopping leader record watcher");
			watch.close();
			watch = null;
		}
	}

	@Override
	public void eventReceived(Action action, ConfigMap configMap) {
		LOGGER.debug("'{}' event received, triggering leadership update", action);

		if (!Action.ERROR.equals(action)) {
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
