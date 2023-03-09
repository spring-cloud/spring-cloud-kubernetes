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

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Kubernetes load balancer client configuration.
 *
 * @author Piotr Minkowski
 */
public class Fabric8LoadBalancerClientConfiguration {

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.loadbalancer.mode", havingValue = "SERVICE")
	ServiceInstanceListSupplier kubernetesServicesListSupplier(Environment environment,
			KubernetesClient kubernetesClient, Fabric8ServiceInstanceMapper mapper,
			KubernetesDiscoveryProperties discoveryProperties, ConfigurableApplicationContext context) {
		return ServiceInstanceListSupplier.builder()
				.withBase(new Fabric8ServicesListSupplier(environment, kubernetesClient, mapper, discoveryProperties))
				.withCaching().build(context);
	}

}
