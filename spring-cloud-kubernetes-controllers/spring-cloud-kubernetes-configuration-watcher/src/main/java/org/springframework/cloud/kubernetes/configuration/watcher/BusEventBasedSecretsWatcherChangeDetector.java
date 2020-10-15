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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import reactor.core.publisher.Mono;

import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
public class BusEventBasedSecretsWatcherChangeDetector extends SecretsWatcherChangeDetector
		implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher applicationEventPublisher;

	private BusProperties busProperties;

	public BusEventBasedSecretsWatcherChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			SecretsPropertySourceLocator secretsPropertySourceLocator, BusProperties busProperties,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(environment, properties, kubernetesClient, strategy, secretsPropertySourceLocator,
				k8SConfigurationProperties, threadPoolTaskExecutor);

		this.busProperties = busProperties;
	}

	@Override
	protected Mono<Void> triggerRefresh(Secret secret) {
		this.applicationEventPublisher.publishEvent(
				new RefreshRemoteApplicationEvent(secret, busProperties.getId(), secret.getMetadata().getName()));
		return Mono.empty();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
