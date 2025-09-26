/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
@Configuration
public class KubernetesDiscoveryClientHealthConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(KubernetesDiscoveryClientHealthConfiguration.class));

	@Bean
	@ConditionalOnClass({ HealthIndicator.class })
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	public KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(
		ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {

		LOG.debug(() -> "Will publish InstanceRegisteredEvent from blocking implementation");
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

}
