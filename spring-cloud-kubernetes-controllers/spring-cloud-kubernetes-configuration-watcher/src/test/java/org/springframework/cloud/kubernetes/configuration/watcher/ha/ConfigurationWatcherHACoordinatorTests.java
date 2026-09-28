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

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapWatcherChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedConfigMapWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedSecretsWatchChangeDetector;
import org.springframework.cloud.kubernetes.configuration.watcher.SecretsWatcherChangeDetector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
		ConfigMapWatcherChangeDetector configMapDetector = mock(HttpBasedConfigMapWatchChangeDetector.class);

		SecretsWatcherChangeDetector secretsDetector = mock(HttpBasedSecretsWatchChangeDetector.class);

		ObjectProvider<ConfigMapWatcherChangeDetector> configMapProvider = mock(ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(configMapDetector);

		doAnswer(invocation -> {
			Consumer<ConfigMapWatcherChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(configMapDetector);
			return null;
		}).when(configMapProvider).ifAvailable(any());

		ObjectProvider<SecretsWatcherChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(secretsDetector);

		doAnswer(invocation -> {
			Consumer<SecretsWatcherChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(secretsDetector);
			return null;
		}).when(secretsProvider).ifAvailable(any());

		ConfigurationWatcherHACoordinator coordinator = new ConfigurationWatcherHACoordinator(configMapProvider,
				secretsProvider);

		coordinator.onStartLeading(new StartLeadingEvent("candidate"));

		verify(configMapDetector).start();
		verify(secretsDetector).start();
	}

	@Test
	void onStopLeadingStopsBothDetectors() {
		ConfigMapWatcherChangeDetector configMapDetector = mock(HttpBasedConfigMapWatchChangeDetector.class);

		SecretsWatcherChangeDetector secretsDetector = mock(HttpBasedSecretsWatchChangeDetector.class);

		ObjectProvider<ConfigMapWatcherChangeDetector> configMapProvider = mock(ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(configMapDetector);

		doAnswer(invocation -> {
			Consumer<ConfigMapWatcherChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(configMapDetector);
			return null;
		}).when(configMapProvider).ifAvailable(any());

		ObjectProvider<SecretsWatcherChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(secretsDetector);

		doAnswer(invocation -> {
			Consumer<SecretsWatcherChangeDetector> consumer = invocation.getArgument(0);
			consumer.accept(secretsDetector);
			return null;
		}).when(secretsProvider).ifAvailable(any());

		ConfigurationWatcherHACoordinator coordinator = new ConfigurationWatcherHACoordinator(configMapProvider,
				secretsProvider);

		coordinator.onStopLeading(new StopLeadingEvent("candidate"));

		verify(configMapDetector).stop();
		verify(secretsDetector).stop();
	}

	@Test
	void failsWhenNeitherDetectorIsAvailable() {
		ObjectProvider<ConfigMapWatcherChangeDetector> configMapProvider = mock(ObjectProvider.class);
		when(configMapProvider.getIfAvailable()).thenReturn(null);
		ObjectProvider<SecretsWatcherChangeDetector> secretsProvider = mock(ObjectProvider.class);
		when(secretsProvider.getIfAvailable()).thenReturn(null);
		assertThatThrownBy(() -> new ConfigurationWatcherHACoordinator(configMapProvider, secretsProvider))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Configuration watcher HA is enabled, but neither ConfigMap nor Secret watching is enabled");
	}

}
