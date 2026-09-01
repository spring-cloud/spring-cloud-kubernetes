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

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
class ConfigurationWatcherHACoordinatorTests {

	@Test
	void onStartLeadingStartsBothDetectors() {
		KubernetesClientEventBasedConfigMapChangeDetector configMapDetector = mock(
				KubernetesClientEventBasedConfigMapChangeDetector.class);

		KubernetesClientEventBasedSecretsChangeDetector secretsDetector = mock(
				KubernetesClientEventBasedSecretsChangeDetector.class);

		ObjectProvider<KubernetesClientEventBasedConfigMapChangeDetector> configMapProvider = mock(
				ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(configMapDetector);

		doAnswer(invocation -> {
			Consumer<KubernetesClientEventBasedConfigMapChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(configMapDetector);
			return null;
		}).when(configMapProvider).ifAvailable(any());

		ObjectProvider<KubernetesClientEventBasedSecretsChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(secretsDetector);

		doAnswer(invocation -> {
			Consumer<KubernetesClientEventBasedSecretsChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(secretsDetector);
			return null;
		}).when(secretsProvider).ifAvailable(any());

		ConfigurationWatcherStateStore stateStore = mock(LeaseConfigurationWatcherStateStore.class);
		when(stateStore.readOrCreate()).thenReturn(
				new ConfigurationWatcherState(Map.of("default", "config-map-rv"), Map.of("default", "secret-rv")));

		ConfigurationWatcherHACoordinator coordinator = new ConfigurationWatcherHACoordinator(configMapProvider,
				secretsProvider, stateStore);

		coordinator.onStartLeading(new StartLeadingEvent("candidate"));

		verify(configMapDetector).start(eq(Map.of("default", "config-map-rv")), any());
		verify(secretsDetector).start(eq(Map.of("default", "secret-rv")), any());
	}

	@Test
	void onStopLeadingStopsBothDetectors() {
		KubernetesClientEventBasedConfigMapChangeDetector configMapDetector = mock(
				KubernetesClientEventBasedConfigMapChangeDetector.class);

		KubernetesClientEventBasedSecretsChangeDetector secretsDetector = mock(
				KubernetesClientEventBasedSecretsChangeDetector.class);

		ObjectProvider<KubernetesClientEventBasedConfigMapChangeDetector> configMapProvider = mock(
				ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(configMapDetector);

		doAnswer(invocation -> {
			Consumer<KubernetesClientEventBasedConfigMapChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(configMapDetector);
			return null;
		}).when(configMapProvider).ifAvailable(any());

		ObjectProvider<KubernetesClientEventBasedSecretsChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(secretsDetector);

		doAnswer(invocation -> {
			Consumer<KubernetesClientEventBasedSecretsChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(secretsDetector);
			return null;
		}).when(secretsProvider).ifAvailable(any());

		ConfigurationWatcherStateStore stateStore = mock(LeaseConfigurationWatcherStateStore.class);

		ConfigurationWatcherHACoordinator coordinator = new ConfigurationWatcherHACoordinator(configMapProvider,
				secretsProvider, stateStore);

		coordinator.onStopLeading(new StopLeadingEvent("candidate"));

		verify(configMapDetector).stop();
		verify(secretsDetector).stop();
	}

	@Test
	void failsWhenNeitherDetectorIsAvailable() {
		ObjectProvider<KubernetesClientEventBasedConfigMapChangeDetector> configMapProvider = mock(
				ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(null);
		ObjectProvider<KubernetesClientEventBasedSecretsChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(null);
		ConfigurationWatcherStateStore stateStore = mock(LeaseConfigurationWatcherStateStore.class);

		assertThatThrownBy(() -> new ConfigurationWatcherHACoordinator(configMapProvider, secretsProvider, stateStore))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Configuration watcher HA is enabled, but neither ConfigMap nor Secret watching is enabled");
	}

}
