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

package org.springframework.cloud.kubernetes.config.reload;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;

/**
 * An Event Based change detector that subscribes to changes in configMaps and fire a
 * reload when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class EventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private final ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	private final Map<String, Watch> watches;

	public EventBasedConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			ConfigMapPropertySourceLocator configMapPropertySourceLocator) {
		super(environment, properties, kubernetesClient, strategy);

		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
		this.watches = new HashMap<>();
	}

	@PostConstruct
	public void watch() {
		boolean activated = false;

		if (this.properties.isMonitoringConfigMaps()) {
			try {
				String name = "config-maps-watch-event";
				this.watches.put(name, this.kubernetesClient.configMaps().watch(new Watcher<ConfigMap>() {
					@Override
					public void eventReceived(Action action, ConfigMap configMap) {
						if (log.isDebugEnabled()) {
							log.debug(name + " received event for ConfigMap " + configMap.getMetadata().getName());
						}
						onEvent(configMap);
					}

					@Override
					public void onClose(KubernetesClientException e) {
					}
				}));
				activated = true;
				this.log.info("Added new Kubernetes watch: " + name);
			}
			catch (Exception e) {
				this.log.error(
						"Error while establishing a connection to watch config maps: configuration may remain stale",
						e);
			}
		}

		if (activated) {
			this.log.info("Kubernetes event-based configMap change detector activated");
		}
	}

	@PreDestroy
	public void unwatch() {
		if (this.watches != null) {
			for (Map.Entry<String, Watch> entry : this.watches.entrySet()) {
				try {
					this.log.debug("Closing the watch " + entry.getKey());
					entry.getValue().close();

				}
				catch (Exception e) {
					this.log.error("Error while closing the watch connection", e);
				}
			}
		}
	}

	protected void onEvent(ConfigMap configMap) {
		boolean changed = changed(locateMapPropertySources(this.configMapPropertySourceLocator, this.environment),
				findPropertySources(ConfigMapPropertySource.class));
		if (changed) {
			this.log.info("Detected change in config maps");
			reloadProperties();
		}
	}

}
