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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;

/**
 * An event based change detector that subscribes to changes in secrets and fire a reload
 * when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class Fabric8EventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private final Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator;

	private final Map<String, Watch> watches;

	private final KubernetesClient kubernetesClient;

	private final boolean monitorSecrets;

	public Fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator) {
		super(environment, properties, strategy);
		this.kubernetesClient = kubernetesClient;
		this.fabric8SecretsPropertySourceLocator = fabric8SecretsPropertySourceLocator;
		this.watches = new HashMap<>();
		this.monitorSecrets = properties.isMonitoringSecrets();
	}

	@PreDestroy
	public void shutdown() {
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

	@PostConstruct
	public void watch() {
		boolean activated = false;

		if (monitorSecrets) {
			try {
				String name = "secrets-watch-event";
				watches.put(name, this.kubernetesClient.secrets().watch(new Watcher<>() {
					@Override
					public void eventReceived(Action action, Secret secret) {
						log.debug(name + " received event for Secret " + secret.getMetadata().getName());
						onEvent(secret);
					}

					@Override
					public void onClose(WatcherException exception) {
						log.warn("Secrets watch closed", exception);
						Optional.ofNullable(exception).map(e -> {
							log.debug("Exception received during watch", e);
							return exception.asClientException();
						}).map(KubernetesClientException::getStatus).map(Status::getCode)
								.filter(c -> c.equals(HttpURLConnection.HTTP_GONE)).ifPresent(c -> watch());
					}
				}));
				activated = true;
				log.info("Added new Kubernetes watch: " + name);
			}
			catch (Exception e) {
				log.error("Error while establishing a connection to watch secrets: configuration may remain stale",
						e);
			}
		}

		if (activated) {
			log.info("Kubernetes event-based secrets change detector activated");
		}
	}

	@PreDestroy
	public void unwatch() {
		for (Map.Entry<String, Watch> entry : watches.entrySet()) {
			try {
				log.debug("Closing the watch " + entry.getKey());
				entry.getValue().close();
			}
			catch (Exception e) {
				log.error("Error while closing the watch connection", e);
			}
		}
	}

	protected void onEvent(Secret secret) {
		log.debug("onEvent secrets: " + secret.toString());
		boolean changed = changed(locateMapPropertySources(fabric8SecretsPropertySourceLocator, environment),
				findPropertySources(Fabric8SecretsPropertySource.class));
		if (changed) {
			log.info("Detected change in secrets");
			reloadProperties();
		}
	}

}
