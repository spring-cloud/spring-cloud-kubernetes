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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.RefreshStrategy;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@ExtendWith(MockitoExtension.class)
class BusEventBasedConfigMapWatcherChangeDetectorTests {

	private static final ConfigurationUpdateStrategy UPDATE_STRATEGY = new ConfigurationUpdateStrategy("strategy",
			() -> {

			});

	@Mock
	private CoreV1Api coreV1Api;

	@Mock
	private KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator;

	private static final ThreadPoolTaskExecutor THREAD_POOL_TASK_EXECUTOR = new ThreadPoolTaskExecutor();

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider;

	private BusProperties busProperties;

	private MockEnvironment mockEnvironment;

	@BeforeEach
	void setup() {
		mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty(NAMESPACE_PROPERTY, "default");
		busProperties = new BusProperties();
	}

	@BeforeAll
	static void beforeAll() {
		THREAD_POOL_TASK_EXECUTOR.setDaemon(true);
	}

	@Test
	void triggerRefreshWithConfigMapUsingServiceNames() {
		ArgumentCaptor<RefreshRemoteApplicationEvent> argumentCaptor = ArgumentCaptor
			.forClass(RefreshRemoteApplicationEvent.class);
		triggerRefreshWithConfigMapUsingServiceNames(RefreshStrategy.REFRESH, argumentCaptor);
	}

	@Test
	void triggerRefreshWithConfigMapUsingServiceNamesAndShutdown() {
		ArgumentCaptor<ShutdownRemoteApplicationEvent> argumentCaptor = ArgumentCaptor
			.forClass(ShutdownRemoteApplicationEvent.class);
		triggerRefreshWithConfigMapUsingServiceNames(RefreshStrategy.SHUTDOWN, argumentCaptor);
	}

	/**
	 * <pre>
	 * 	- watcher-level configMapApps is configured
	 * 	- configMap also has spring.cloud.kubernetes.configmap.apps
	 * 	- watcher-level configured apps must win when onEvent builds the KubernetesSource
	 * 	  ( meaning properties that are configured in the watcher itself take precedence
	 * 	    over the ones coming from the configmap )
	 * </pre>
	 */
	@Test
	void onEventUsesConfiguredConfigMapAppsInsteadOfAnnotationApps() {

		List<String> configmapApps = List.of("app-from-property");
		List<String> secretApps = List.of();

		ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		configurationWatcherConfigurationProperties.setRefreshDelay(Duration.ZERO);

		BusEventBasedConfigMapWatcherChangeDetector changeDetector = new BusEventBasedConfigMapWatcherChangeDetector(
				coreV1Api, mockEnvironment, configReloadProperties(configmapApps, secretApps), UPDATE_STRATEGY,
				configMapPropertySourceLocator, new KubernetesNamespaceProvider(mockEnvironment),
				configurationWatcherConfigurationProperties, THREAD_POOL_TASK_EXECUTOR,
				new BusRefreshTrigger(applicationEventPublisher, busProperties.getId(),
						configurationWatcherConfigurationProperties, reactiveDiscoveryClientProvider));

		changeDetector.onEvent(
				new V1ConfigMapBuilder()
					.withMetadata(new V1ObjectMeta().name("my-configmap")
						.annotations(Map.of(ConfigMapKubernetesSource.CONFIGMAP_SERVICE_NAMES_ANNOTATION,
								"app-from-annotation")))
					.build());

		verify(applicationEventPublisher, timeout(1000)).publishEvent(argThat(event -> {
			if (!(event instanceof RefreshRemoteApplicationEvent refreshRemoteApplicationEvent)) {
				return false;
			}
			KubernetesSource kubernetesSource = (KubernetesSource) refreshRemoteApplicationEvent.getSource();
			return kubernetesSource.serviceNames().equals(Set.of("app-from-property"))
					&& refreshRemoteApplicationEvent.getDestinationService().equals("app-from-property:**");
		}));
	}

	void triggerRefreshWithConfigMapUsingServiceNames(RefreshStrategy refreshStrategy,
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

		Set<String> names = Set.of("foo");
		// labels are empty so that we test the names branching
		Map<String, String> labels = Map.of();
		String resourceName = "foo";

		KubernetesSource configMapKubernetesSource = new ConfigMapKubernetesSource(names, labels, resourceName);

		ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		configurationWatcherConfigurationProperties.setRefreshStrategy(refreshStrategy);
		BusEventBasedConfigMapWatcherChangeDetector changeDetector = new BusEventBasedConfigMapWatcherChangeDetector(
				coreV1Api, mockEnvironment, ConfigReloadProperties.DEFAULT, UPDATE_STRATEGY,
				configMapPropertySourceLocator, new KubernetesNamespaceProvider(mockEnvironment),
				configurationWatcherConfigurationProperties, THREAD_POOL_TASK_EXECUTOR,
				new BusRefreshTrigger(applicationEventPublisher, busProperties.getId(),
						configurationWatcherConfigurationProperties, reactiveDiscoveryClientProvider));
		Mono<Void> result = changeDetector.triggerRefresh(configMapKubernetesSource);
		StepVerifier.create(result).verifyComplete();
	}

	private ConfigReloadProperties configReloadProperties(List<String> configMapApps, List<String> secretApps) {
		return new ConfigReloadProperties(true, true, Map.of(), false, Map.of(),
				ConfigReloadProperties.DEFAULT.strategy(), ConfigReloadProperties.DEFAULT.mode(),
				ConfigReloadProperties.DEFAULT.period(), ConfigReloadProperties.DEFAULT.namespaces(),
				ConfigReloadProperties.DEFAULT.maxWaitForRestart(), configMapApps, secretApps);
	}

}
