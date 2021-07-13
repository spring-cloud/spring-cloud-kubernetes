/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8;

import io.fabric8.kubernetes.api.model.Pod;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnKubernetesEnabled
public class Fabric8ActuatorConfiguration {

	@Bean
	@ConditionalOnEnabledHealthIndicator("kubernetes")
	public Fabric8HealthIndicator kubernetesHealthIndicator(PodUtils<Pod> podUtils) {
		return new Fabric8HealthIndicator(podUtils);
	}

	@Bean
	@ConditionalOnEnabledInfoContributor("kubernetes")
	public Fabric8InfoContributor kubernetesInfoContributor(PodUtils<Pod> podUtils) {
		return new Fabric8InfoContributor(podUtils);
	}

}
