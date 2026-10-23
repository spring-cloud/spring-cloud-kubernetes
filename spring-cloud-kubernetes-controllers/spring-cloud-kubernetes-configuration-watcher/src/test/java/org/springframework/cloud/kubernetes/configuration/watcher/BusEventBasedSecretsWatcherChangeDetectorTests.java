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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.event.ShutdownRemoteApplicationEvent;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.RefreshStrategy;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@ExtendWith(MockitoExtension.class)
class BusEventBasedSecretsWatcherChangeDetectorTests {

	private static final ThreadPoolTaskExecutor THREAD_POOL_TASK_EXECUTOR = new ThreadPoolTaskExecutor();

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider;

	@Mock
	private KubernetesClientEventBasedSecretsChangeDetector secretsChangeDetector;

	private BusProperties busProperties;

	@BeforeEach
	void setup() {
		busProperties = new BusProperties();
	}

	@BeforeAll
	static void beforeAll() {
		THREAD_POOL_TASK_EXECUTOR.setDaemon(true);
	}

	/**
	 * Test set-up. <pre>
	 * - start the bus Secret watcher
	 * - verify that starting the wrapper starts the underlying Kubernetes detector
	 * - stop the bus Secret watcher
	 * - verify that stopping the wrapper stops the underlying Kubernetes detector
	 * </pre>
	 */
	@Test
	void startAndStopDelegateToSecretsChangeDetector() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		BusRefreshTrigger refreshTrigger = new BusRefreshTrigger(applicationEventPublisher, busProperties.getId(),
				properties, reactiveDiscoveryClientProvider);
		BusEventBasedSecretsWatcherChangeDetector detector = new BusEventBasedSecretsWatcherChangeDetector(
				secretsChangeDetector, ConfigReloadProperties.DEFAULT, properties, THREAD_POOL_TASK_EXECUTOR,
				refreshTrigger);

		detector.start();
		detector.stop();

		verify(secretsChangeDetector).start(any());
		verify(secretsChangeDetector).stop();
	}

	@Test
	void triggerRefreshWithSecretUsingServiceNames() {
		triggerRefreshWithSecretUsingServiceNames(RefreshStrategy.REFRESH,
				ArgumentCaptor.forClass(RefreshRemoteApplicationEvent.class));
	}

	@Test
	void triggerRefreshWithSecretUsingServiceNamesAndShutdown() {
		triggerRefreshWithSecretUsingServiceNames(RefreshStrategy.SHUTDOWN,
				ArgumentCaptor.forClass(ShutdownRemoteApplicationEvent.class));
	}

	/**
	 * The watcher-level secretApps setting takes precedence over app names on the Secret
	 * annotation.
	 */
	@Test
	void onEventUsesConfiguredSecretAppsInsteadOfAnnotationApps() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		properties.setRefreshDelay(Duration.ZERO);
		BusRefreshTrigger refreshTrigger = new BusRefreshTrigger(applicationEventPublisher, busProperties.getId(),
				properties, reactiveDiscoveryClientProvider);
		BusEventBasedSecretsWatcherChangeDetector detector = new BusEventBasedSecretsWatcherChangeDetector(
				secretsChangeDetector, configReloadProperties(List.of(), List.of("app-from-property")), properties,
				THREAD_POOL_TASK_EXECUTOR, refreshTrigger);
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta().name("my-secret")
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "app-from-annotation")))
			.build();
		doAnswer(invocation -> {
			Consumer<V1Secret> onEvent = invocation.getArgument(0);
			onEvent.accept(secret);
			return null;
		}).when(secretsChangeDetector).start(any());

		detector.start();

		verify(applicationEventPublisher, timeout(1000)).publishEvent(argThat(event -> {
			if (!(event instanceof RefreshRemoteApplicationEvent refreshRemoteApplicationEvent)) {
				return false;
			}
			KubernetesSource kubernetesSource = (KubernetesSource) refreshRemoteApplicationEvent.getSource();
			return kubernetesSource.serviceNames().equals(Set.of("app-from-property"))
					&& refreshRemoteApplicationEvent.getDestinationService().equals("app-from-property:**");
		}));

		detector.stop();
	}

	void triggerRefreshWithSecretUsingServiceNames(RefreshStrategy refreshStrategy,
			ArgumentCaptor<? extends RemoteApplicationEvent> argumentCaptor) {
		triggerRefresh(refreshStrategy);
		verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());
		KubernetesSource kubernetesSource = (KubernetesSource) argumentCaptor.getValue().getSource();
		assertThat(kubernetesSource.resourceName()).isEqualTo("foo");
		assertThat(kubernetesSource.serviceNames()).isEqualTo(Set.of("foo"));
		assertThat(kubernetesSource.serviceLabels()).isEqualTo(Map.of());
		assertThat(argumentCaptor.getValue().getOriginService()).isEqualTo(busProperties.getId());
		assertThat(argumentCaptor.getValue().getDestinationService()).isEqualTo("foo:**");
	}

	private void triggerRefresh(RefreshStrategy refreshStrategy) {
		KubernetesSource secretKubernetesSource = new SecretKubernetesSource(Set.of("foo"), Map.of(), "foo");
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		properties.setRefreshStrategy(refreshStrategy);
		BusEventBasedSecretsWatcherChangeDetector detector = new BusEventBasedSecretsWatcherChangeDetector(
				secretsChangeDetector, ConfigReloadProperties.DEFAULT, properties, THREAD_POOL_TASK_EXECUTOR,
				new BusRefreshTrigger(applicationEventPublisher, busProperties.getId(), properties,
						reactiveDiscoveryClientProvider));
		Mono<Void> result = detector.triggerRefresh(secretKubernetesSource);
		StepVerifier.create(result).verifyComplete();
	}

	private ConfigReloadProperties configReloadProperties(List<String> configMapApps, List<String> secretApps) {
		return new ConfigReloadProperties(true, true, Map.of(), false, Map.of(),
				ConfigReloadProperties.DEFAULT.strategy(), ConfigReloadProperties.DEFAULT.mode(),
				ConfigReloadProperties.DEFAULT.period(), ConfigReloadProperties.DEFAULT.namespaces(),
				ConfigReloadProperties.DEFAULT.maxWaitForRestart(), configMapApps, secretApps);
	}

}
