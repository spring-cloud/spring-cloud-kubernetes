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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySource;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;

/**
 * A change detector that subscribes to changes in secrets and configmaps and fire a
 * reload when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 */
public class EventBasedConfigurationChangeDetector extends ConfigurationChangeDetector {

	private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	private SecretsPropertySourceLocator secretsPropertySourceLocator;

	private Map<String, Watch> watches;

	public EventBasedConfigurationChangeDetector(AbstractEnvironment environment,
			ConfigReloadProperties properties, KubernetesClient kubernetesClient,
			ConfigurationUpdateStrategy strategy,
			ConfigMapPropertySourceLocator configMapPropertySourceLocator,
			SecretsPropertySourceLocator secretsPropertySourceLocator) {
		super(environment, properties, kubernetesClient, strategy);

		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
		this.secretsPropertySourceLocator = secretsPropertySourceLocator;
		this.watches = new ConcurrentHashMap<>();
	}

	@PostConstruct
	public void watch() {
		boolean activated = false;

		if (this.properties.isMonitoringConfigMaps()
				&& this.configMapPropertySourceLocator != null) {
			try {
				String name = "config-maps-watch";
				watchConfigMap(name);
				activated = true;
				this.log.info("Added new Kubernetes watch: " + name);
			}
			catch (Exception e) {
				this.log.error(
						"Error while establishing a connection to watch config maps: configuration may remain stale",
						e);
			}
		}

		if (this.properties.isMonitoringSecrets()
				&& this.secretsPropertySourceLocator != null) {
			try {
				activated = false;
				String name = "secrets-watch";
				watchSecret(name);
				activated = true;
				this.log.info("Added new Kubernetes watch: " + name);
			}
			catch (Exception e) {
				this.log.error(
						"Error while establishing a connection to watch secrets: configuration may remain stale",
						e);
			}
		}

		if (activated) {
			this.log.info(
					"Kubernetes event-based configuration change detector activated");
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

	private void watchConfigMap(final String name) {
		Watcher<ConfigMap> watcher = new Watcher<ConfigMap>() {
			@Override
			public void eventReceived(Action action, ConfigMap configMap) {
				onEvent(configMap);
			}

			@Override
			public void onClose(KubernetesClientException error) {
				if (error != null) {
					if (log.isDebugEnabled()) {
						log.warn("Try to re-watch: " + name, error);
					}
					watchConfigMap(name);
				}
			}
		};
		this.watches.put(name, this.kubernetesClient.configMaps().watch(watcher));
	}

	private void watchSecret(final String name) {
		Watcher<Secret> watcher = new Watcher<Secret>() {
			@Override
			public void eventReceived(Action action, Secret secret) {
				onEvent(secret);
			}

			@Override
			public void onClose(KubernetesClientException error) {
				if (error != null) {
					if (log.isDebugEnabled()) {
						log.warn("Try to re-watch: " + name, error);
					}
					watchSecret(name);
				}
			}
		};
		this.watches.put(name, this.kubernetesClient.secrets().watch(watcher));
	}

	private void onEvent(ConfigMap configMap) {
		boolean changed = changed(
				locateMapPropertySources(this.configMapPropertySourceLocator,
						this.environment),
				findPropertySources(ConfigMapPropertySource.class));
		if (changed) {
			this.log.info("Detected change in config maps");
			reloadProperties();
		}
	}

	private void onEvent(Secret secret) {
		boolean changed = changed(
				locateMapPropertySources(this.secretsPropertySourceLocator,
						this.environment),
				findPropertySources(SecretsPropertySource.class));
		if (changed) {
			this.log.info("Detected change in secrets");
			reloadProperties();
		}
	}

}
