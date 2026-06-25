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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.event.ShutdownRemoteApplicationEvent;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.RefreshStrategy;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@ExtendWith(MockitoExtension.class)
class BusEventBasedSecretsWatcherChangeDetectorTests {

	private static final ConfigurationUpdateStrategy UPDATE_STRATEGY = new ConfigurationUpdateStrategy("strategy",
			() -> {

			});

	@Mock
	private CoreV1Api coreV1Api;

	@Mock
	private KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator;

	@Mock
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	private BusProperties busProperties;

	private MockEnvironment mockEnvironment;

	@BeforeEach
	void setup() {
		mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty(NAMESPACE_PROPERTY, "default");
		busProperties = new BusProperties();
	}

	@Test
	void triggerRefreshWithSecret() {
		ArgumentCaptor<RefreshRemoteApplicationEvent> argumentCaptor = ArgumentCaptor
			.forClass(RefreshRemoteApplicationEvent.class);
		triggerRefreshWithSecret(RefreshStrategy.REFRESH, argumentCaptor);
	}

	@Test
	void triggerRefreshWithSecretWithShutdown() {
		ArgumentCaptor<ShutdownRemoteApplicationEvent> argumentCaptor = ArgumentCaptor
			.forClass(ShutdownRemoteApplicationEvent.class);
		triggerRefreshWithSecret(RefreshStrategy.SHUTDOWN, argumentCaptor);
	}

	void triggerRefreshWithSecret(RefreshStrategy refreshStrategy,
			ArgumentCaptor<? extends RemoteApplicationEvent> argumentCaptor) {

		triggerRefresh(refreshStrategy);

		verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());

		KubernetesSource kubernetesSource = (KubernetesSource) argumentCaptor.getValue().getSource();

		assertThat(kubernetesSource.resourceName()).isEqualTo("foo");
		assertThat(kubernetesSource.serviceNames()).isEqualTo(Set.of("foo"));
		assertThat(kubernetesSource.serviceLabels()).isEqualTo(Map.of("a", "b"));
		assertThat(argumentCaptor.getValue().getOriginService()).isEqualTo(busProperties.getId());
		assertThat(argumentCaptor.getValue().getDestinationService()).isEqualTo("foo:**");
	}

	private void triggerRefresh(RefreshStrategy refreshStrategy) {

		KubernetesSource secretKubernetesSource = new SecretKubernetesSource(Set.of("foo"), Map.of("a", "b"), "foo");

		ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		configurationWatcherConfigurationProperties.setRefreshStrategy(refreshStrategy);
		BusEventBasedSecretsWatcherChangeDetector changeDetector = new BusEventBasedSecretsWatcherChangeDetector(
				coreV1Api, mockEnvironment, ConfigReloadProperties.DEFAULT, UPDATE_STRATEGY,
				secretsPropertySourceLocator, new KubernetesNamespaceProvider(mockEnvironment),
				configurationWatcherConfigurationProperties, threadPoolTaskExecutor, new BusRefreshTrigger(
						applicationEventPublisher, busProperties.getId(), configurationWatcherConfigurationProperties));
		changeDetector.triggerRefresh(secretKubernetesSource);
	}

}
