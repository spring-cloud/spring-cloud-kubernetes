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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

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
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * A change detector that subscribes to changes in secrets and configmaps and fire a
 * reload when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 */
public class EventBasedConfigurationChangeDetector extends ConfigurationChangeDetector {

	private final ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	private final SecretsPropertySourceLocator secretsPropertySourceLocator;

	private final Map<String, Watch> watches = new ConcurrentHashMap<>();

	private final RetryContext retryContext = new RetryContext();

	public EventBasedConfigurationChangeDetector(AbstractEnvironment environment,
			ConfigReloadProperties properties, KubernetesClient kubernetesClient,
			ConfigurationUpdateStrategy strategy,
			ConfigMapPropertySourceLocator configMapPropertySourceLocator,
			SecretsPropertySourceLocator secretsPropertySourceLocator) {
		super(environment, properties, kubernetesClient, strategy);
		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
		this.secretsPropertySourceLocator = secretsPropertySourceLocator;
	}

	@PostConstruct
	public void watch() {
		boolean activated = false;

		if (this.properties.isMonitoringConfigMaps()
				&& this.configMapPropertySourceLocator != null) {
			try {
				watchConfigMap();
				activated = true;
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
				watchSecret();
				activated = true;
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
		for (Map.Entry<String, Watch> entry : this.watches.entrySet()) {
			try {
				this.log.debug("Closing the watch " + entry.getKey());
				entry.getValue().close();
			}
			catch (Exception e) {
				this.log.error("Error while closing the watch connection", e);
			}
		}
		retryContext.shutdown();
	}

	private void watchConfigMap() {
		Watcher<ConfigMap> watcher = new WatchResource<>("config maps",
				this::isConfigMapChanged, this::watchConfigMap);
		Watch watch = kubernetesClient.configMaps().watch(watcher);
		watches.put("config-maps-watch", watch);
		log.info("Added new Kubernetes watch: config-maps-watch");
	}

	private void watchSecret() {
		Watcher<Secret> watcher = new WatchResource<>("secrets", this::isSecretChanged,
				this::watchSecret);
		Watch watch = kubernetesClient.secrets().watch(watcher);
		watches.put("secrets-watch", watch);
		log.info("Added new Kubernetes watch: secrets-watch");
	}

	private boolean isConfigMapChanged() {
		return changed(
				locateMapPropertySources(configMapPropertySourceLocator, environment),
				findPropertySources(ConfigMapPropertySource.class));
	}

	private boolean isSecretChanged() {
		return changed(
				locateMapPropertySources(secretsPropertySourceLocator, environment),
				findPropertySources(SecretsPropertySource.class));
	}

	private class WatchResource<T> implements Watcher<T> {

		private final String resourceType;

		private final BooleanSupplier conditions;

		private final Runnable reconnect;

		WatchResource(final String resourceType, final BooleanSupplier conditions,
				final Runnable reconnect) {
			this.resourceType = resourceType;
			this.conditions = conditions;
			this.reconnect = reconnect;
		}

		@Override
		public void eventReceived(final Action action, final T resource) {
			retryContext.reset(resourceType);
			if (conditions.getAsBoolean()) {
				log.info("Detected change in " + resourceType);
				reloadProperties();
			}
		}

		@Override
		public void onClose(final KubernetesClientException cause) {
			if (cause != null) {
				log.debug("Watch connection is closed. Try to re-watch " + resourceType,
						cause);
				retryContext.retry(resourceType, reconnect);
			}
		}

	}

	private class RetryContext {

		private final BackOff backOff = new ExponentialBackOff();

		private final Map<String, BackOffExecution> backOffExecutions = new ConcurrentHashMap<>();

		private final AtomicReference<ScheduledExecutorService> executorReference = new AtomicReference<>();

		public void retry(final String backOffKey, final Runnable task) {
			BackOffExecution exec = backOffExecutions.computeIfAbsent(backOffKey,
					key -> backOff.start());
			long waitInterval = exec.nextBackOff();
			if (waitInterval != BackOffExecution.STOP) {
				ScheduledExecutorService service = getExecutorService();
				service.schedule(task, waitInterval, TimeUnit.MILLISECONDS);
			}
			else {
				log.error("Give up to re-watch: " + backOffKey);
			}
		}

		public void reset(final String name) {
			backOffExecutions.remove(name);
		}

		public void shutdown() {
			backOffExecutions.clear();
			executorReference.updateAndGet(current -> {
				if (current != null) {
					current.shutdown();
				}
				return null;
			});
		}

		private ScheduledExecutorService getExecutorService() {
			return executorReference.updateAndGet(current -> {
				if (current != null) {
					return current;
				}
				return Executors.newSingleThreadScheduledExecutor();
			});
		}

	}

}
