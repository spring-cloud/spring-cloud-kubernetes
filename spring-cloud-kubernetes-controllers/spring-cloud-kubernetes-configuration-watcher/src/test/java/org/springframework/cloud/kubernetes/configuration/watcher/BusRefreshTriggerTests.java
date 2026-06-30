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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.event.ShutdownRemoteApplicationEvent;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusRefreshTriggerTests {

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider;

	@Mock
	private ReactiveDiscoveryClient reactiveDiscoveryClient;

	/**
	 * <pre>
	 *     - there are three discovered service instances
	 *     - two of them belong to service app-one and match the requested labels
	 *     - one belongs to app-two and does not match
	 *     - bus refresh should publish exactly one event for app-one
	 * </pre>
	 */
	@Test
	void triggerRefreshWithConfigMapUsingServiceLabels() {
		BusRefreshTrigger refreshTrigger = refreshTrigger(
				ConfigurationWatcherConfigurationProperties.RefreshStrategy.REFRESH);

		when(reactiveDiscoveryClientProvider.getIfAvailable()).thenReturn(reactiveDiscoveryClient);
		when(reactiveDiscoveryClient.getServices()).thenReturn(Flux.just("app-one", "app-two"));
		when(reactiveDiscoveryClient.getInstances(eq("app-one")))
			.thenReturn(Flux.just(serviceInstance("app-one", "app-one-1", Map.of("app", "demo", "tier", "backend")),
					serviceInstance("app-one", "app-one-2", Map.of("app", "demo", "tier", "backend"))));
		when(reactiveDiscoveryClient.getInstances(eq("app-two")))
			.thenReturn(Flux.just(serviceInstance("app-two", "app-two-1", Map.of("app", "demo", "tier", "frontend"))));

		Map<String, String> labels = Map.of("app", "demo", "tier", "backend");
		KubernetesSource source = new ConfigMapKubernetesSource(Set.of("ignored-name"), labels, "my-configmap");

		StepVerifier.create(refreshTrigger.triggerRefresh(source)).verifyComplete();

		ArgumentCaptor<RemoteApplicationEvent> argumentCaptor = ArgumentCaptor.forClass(RemoteApplicationEvent.class);
		verify(applicationEventPublisher, times(1)).publishEvent(argumentCaptor.capture());

		assertThat(argumentCaptor.getValue()).isInstanceOf(RefreshRemoteApplicationEvent.class);
		assertThat(argumentCaptor.getValue().getSource()).isEqualTo(source);
		assertThat(argumentCaptor.getValue().getOriginService()).isEqualTo("bus-id");
		assertThat(argumentCaptor.getValue().getDestinationService()).isEqualTo("app-one:**");
	}

	/**
	 * <pre>
	 *     - a secret targets applications by labels
	 *     - the discovered service matches those labels
	 *     - shutdown strategy publishes a shutdown bus event
	 * </pre>
	 */
	@Test
	void triggerRefreshWithSecretUsingServiceLabelsAndShutdown() {
		BusRefreshTrigger refreshTrigger = refreshTrigger(
				ConfigurationWatcherConfigurationProperties.RefreshStrategy.SHUTDOWN);

		when(reactiveDiscoveryClientProvider.getIfAvailable()).thenReturn(reactiveDiscoveryClient);
		when(reactiveDiscoveryClient.getServices()).thenReturn(Flux.just("app-one"));
		when(reactiveDiscoveryClient.getInstances(eq("app-one")))
			.thenReturn(Flux.just(serviceInstance("app-one", "app-one-1", Map.of("app", "demo"))));

		Map<String, String> labels = Map.of("app", "demo");
		KubernetesSource source = new SecretKubernetesSource(Set.of("ignored-name"), labels, "my-secret");

		StepVerifier.create(refreshTrigger.triggerRefresh(source)).verifyComplete();

		ArgumentCaptor<RemoteApplicationEvent> argumentCaptor = ArgumentCaptor.forClass(RemoteApplicationEvent.class);
		verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());

		assertThat(argumentCaptor.getValue()).isInstanceOf(ShutdownRemoteApplicationEvent.class);
		assertThat(argumentCaptor.getValue().getSource()).isEqualTo(source);
		assertThat(argumentCaptor.getValue().getOriginService()).isEqualTo("bus-id");
		assertThat(argumentCaptor.getValue().getDestinationService()).isEqualTo("app-one:**");
	}

	@Test
	void failsWhenServiceLabelsAreUsedButNoReactiveDiscoveryClientIsAvailable() {
		BusRefreshTrigger refreshTrigger = refreshTrigger(
				ConfigurationWatcherConfigurationProperties.RefreshStrategy.REFRESH);

		when(reactiveDiscoveryClientProvider.getIfAvailable()).thenReturn(null);

		KubernetesSource source = new ConfigMapKubernetesSource(Set.of("ignored-name"), Map.of("app", "demo"),
				"my-configmap");

		assertThatThrownBy(() -> refreshTrigger.triggerRefresh(source)).isInstanceOf(IllegalStateException.class)
			.hasMessage("Using spring.cloud.kubernetes.configmap.labels or "
					+ "spring.cloud.kubernetes.secret.labels with bus refresh requires a ReactiveDiscoveryClient");
	}

	private BusRefreshTrigger refreshTrigger(
			ConfigurationWatcherConfigurationProperties.RefreshStrategy refreshStrategy) {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		properties.setRefreshStrategy(refreshStrategy);
		return new BusRefreshTrigger(applicationEventPublisher, "bus-id", properties, reactiveDiscoveryClientProvider);
	}

	private DefaultKubernetesServiceInstance serviceInstance(String serviceId, String instanceId,
			Map<String, String> metadata) {
		return new DefaultKubernetesServiceInstance(instanceId, serviceId, "localhost", 8080, metadata, false,
				"default", null, Map.of());
	}

}
