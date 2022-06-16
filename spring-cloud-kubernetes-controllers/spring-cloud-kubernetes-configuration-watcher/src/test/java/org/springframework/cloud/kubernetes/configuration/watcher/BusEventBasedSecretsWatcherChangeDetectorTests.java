/*
 * Copyright 2013-2020 the original author or authors.
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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
	private KubernetesClient client;

	@Mock
	private Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator;

	@Mock
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	private BusEventBasedSecretsWatcherChangeDetector changeDetector;

	private BusProperties busProperties;

	@BeforeEach
	void setup() {
		MockEnvironment mockEnvironment = new MockEnvironment();
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		busProperties = new BusProperties();
		changeDetector = new BusEventBasedSecretsWatcherChangeDetector(mockEnvironment, configReloadProperties, client,
				UPDATE_STRATEGY, fabric8SecretsPropertySourceLocator, busProperties,
				configurationWatcherConfigurationProperties, threadPoolTaskExecutor);
		changeDetector.setApplicationEventPublisher(applicationEventPublisher);
	}

	@Test
	void triggerRefreshWithSecret() {
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("foo");
		Secret secret = new Secret();
		secret.setMetadata(objectMeta);
		changeDetector.triggerRefresh(secret);
		ArgumentCaptor<RefreshRemoteApplicationEvent> argumentCaptor = ArgumentCaptor
				.forClass(RefreshRemoteApplicationEvent.class);
		verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());
		assertThat(argumentCaptor.getValue().getSource()).isEqualTo(secret);
		assertThat(argumentCaptor.getValue().getOriginService()).isEqualTo(busProperties.getId());
		assertThat(argumentCaptor.getValue().getDestinationService()).isEqualTo("foo:**");
	}

}
