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

package org.springframework.cloud.kubernetes.configuration.watcher.ha;

/**
 * Persistent storage for HA watcher state shared across watcher instances.
 *
 * @author wind57
 */
sealed interface ConfigurationWatcherStateStore permits LeaseConfigurationWatcherStateStore {

	/**
	 * Reads or creates the single persisted state used by the configuration watcher.
	 *
	 * <p>
	 * When HA is enabled, the leader calls this once before starting the ConfigMap and
	 * Secret informers. The returned state contains checkpoints for all configured
	 * namespaces, so this method must not be called once per namespace. It may be called
	 * again if a later leadership acquisition starts the watcher again.
	 * @return the persisted state, or an empty state when no checkpoint exists
	 */
	ConfigurationWatcherState readOrCreate();

	void writeConfigMapResourceVersion(String namespace, String resourceVersion);

	void writeSecretResourceVersion(String namespace, String resourceVersion);

}
