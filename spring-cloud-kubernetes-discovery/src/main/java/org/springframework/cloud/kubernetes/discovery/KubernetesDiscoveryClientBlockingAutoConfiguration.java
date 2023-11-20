/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@EnableConfigurationProperties({ DiscoveryClientHealthIndicatorProperties.class,
		KubernetesDiscoveryClientProperties.class })
class KubernetesDiscoveryClientBlockingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}

	@Bean
	@ConditionalOnMissingBean
	KubernetesDiscoveryClient kubernetesDiscoveryClient(RestTemplate restTemplate,
			KubernetesDiscoveryClientProperties properties) {
		return new KubernetesDiscoveryClient(restTemplate, properties);
	}

	@Bean
	@ConditionalOnClass({ HealthIndicator.class })
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	InitializingBean indicatorInitializer(ApplicationEventPublisher applicationEventPublisher,
			ApplicationContext applicationContext) {
		InitializingBean bean = () -> applicationEventPublisher
				.publishEvent(new InstanceRegisteredEvent<>(applicationContext.getId(), null));
		return bean;

	}

}
