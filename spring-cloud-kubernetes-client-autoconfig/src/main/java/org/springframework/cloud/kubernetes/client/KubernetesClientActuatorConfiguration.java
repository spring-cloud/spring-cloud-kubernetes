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

package org.springframework.cloud.kubernetes.client;

import io.kubernetes.client.openapi.models.V1Pod;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.autoconfig.ConditionalOnKubernetesHealthIndicatorEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesHealthIndicatorEnabled
public class KubernetesClientActuatorConfiguration {

	@Bean
	@ConditionalOnEnabledHealthIndicator("kubernetes")
	public KubernetesClientHealthIndicator kubernetesHealthIndicator(PodUtils<V1Pod> podUtils) {
		return new KubernetesClientHealthIndicator(podUtils);
	}

	@Bean
	@ConditionalOnEnabledInfoContributor("kubernetes")
	public KubernetesClientInfoContributor kubernetesInfoContributor(PodUtils<V1Pod> podUtils) {
		return new KubernetesClientInfoContributor(podUtils);
	}

}
