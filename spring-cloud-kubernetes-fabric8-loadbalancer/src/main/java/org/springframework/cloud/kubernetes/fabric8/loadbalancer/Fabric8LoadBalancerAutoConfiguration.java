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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes load balancer auto-configuration.
 *
 * @author Piotr Minkowski
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KubernetesLoadBalancerProperties.class)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.loadbalancer.enabled", matchIfMissing = true)
@LoadBalancerClients(defaultConfiguration = Fabric8LoadBalancerClientConfiguration.class)
public class Fabric8LoadBalancerAutoConfiguration {

	@Bean
	Fabric8ServiceInstanceMapper mapper(KubernetesLoadBalancerProperties properties,
			KubernetesDiscoveryProperties discoveryProperties) {
		return new Fabric8ServiceInstanceMapper(properties, discoveryProperties);
	}

}
