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

import java.time.Instant;

import org.jspecify.annotations.NonNull;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.NamespaceAndResourceVersion;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.log.LogAccessor;

/**
 * Coordinates the lifecycle of the ConfigMap and Secret watchers.
 *
 * @author wind57
 */
final class ConfigurationWatcherHACoordinator implements ApplicationListener<@NonNull ApplicationEvent> {

	private static final LogAccessor LOG = new LogAccessor(ConfigurationWatcherHACoordinator.class);

	private final ObjectProvider<@NonNull KubernetesClientEventBasedConfigMapChangeDetector> configMapDetector;

	private final ObjectProvider<@NonNull KubernetesClientEventBasedSecretsChangeDetector> secretsDetector;

	private final ConfigurationWatcherStateStore stateStore;

	ConfigurationWatcherHACoordinator(
			ObjectProvider<@NonNull KubernetesClientEventBasedConfigMapChangeDetector> configMapDetector,
			ObjectProvider<@NonNull KubernetesClientEventBasedSecretsChangeDetector> secretsDetector,
			ConfigurationWatcherStateStore stateStore) {
		if (configMapDetector.getIfAvailable() == null && secretsDetector.getIfAvailable() == null) {
			throw new IllegalStateException(
					"Configuration watcher HA is enabled, but neither ConfigMap nor Secret " + "watching is enabled");
		}
		this.configMapDetector = configMapDetector;
		this.secretsDetector = secretsDetector;
		this.stateStore = stateStore;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof StartLeadingEvent startLeadingEvent) {
			onStartLeading(startLeadingEvent);
		}
		else if (event instanceof StopLeadingEvent stopLeadingEvent) {
			onStopLeading(stopLeadingEvent);
		}
	}

	void onStartLeading(StartLeadingEvent event) {
		LOG.info(() -> "configuration watcher with identity : " + event.candidateIdentity() + " became leader at : "
				+ Instant.ofEpochMilli(event.getTimestamp()));
		ConfigurationWatcherState state = stateStore.readOrCreate();
		configMapDetector.ifAvailable(
				detector -> detector.start(state.configMapResourceVersions(), this::writeConfigMapResourceVersion));
		secretsDetector
			.ifAvailable(detector -> detector.start(state.secretResourceVersions(), this::writeSecretResourceVersion));
	}

	void onStopLeading(StopLeadingEvent event) {
		LOG.info(() -> "configuration watcher with identity : " + event.candidateIdentity()
				+ " stopped being a leader at : " + Instant.ofEpochMilli(event.getTimestamp()));
		secretsDetector.ifAvailable(KubernetesClientEventBasedSecretsChangeDetector::stop);
		configMapDetector.ifAvailable(KubernetesClientEventBasedConfigMapChangeDetector::stop);
	}

	private void writeConfigMapResourceVersion(NamespaceAndResourceVersion resourceVersion) {
		stateStore.writeConfigMapResourceVersion(resourceVersion.namespace(), resourceVersion.resourceVersion());
	}

	private void writeSecretResourceVersion(NamespaceAndResourceVersion resourceVersion) {
		stateStore.writeSecretResourceVersion(resourceVersion.namespace(), resourceVersion.resourceVersion());
	}

}
