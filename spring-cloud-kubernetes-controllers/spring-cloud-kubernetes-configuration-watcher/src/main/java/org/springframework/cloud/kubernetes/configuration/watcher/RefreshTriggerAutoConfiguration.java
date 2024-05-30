/*
 * Copyright 2013-2024 the original author or authors.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
class RefreshTriggerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@Profile({ AMQP, KAFKA })
	BusRefreshTrigger busRefreshTrigger(ApplicationEventPublisher applicationEventPublisher,
			BusProperties busProperties) {
		return new BusRefreshTrigger(applicationEventPublisher, busProperties.getId());
	}

	@Bean
	@ConditionalOnMissingBean
	HttpRefreshTrigger httpRefreshTrigger(KubernetesInformerReactiveDiscoveryClient client,
			ConfigurationWatcherConfigurationProperties properties, WebClient webClient) {
		return new HttpRefreshTrigger(client, properties, webClient);
	}

}
