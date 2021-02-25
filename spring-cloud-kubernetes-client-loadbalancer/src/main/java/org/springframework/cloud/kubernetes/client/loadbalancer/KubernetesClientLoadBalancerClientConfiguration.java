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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientLoadBalancerClientConfiguration {

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.loadbalancer.mode", havingValue = "SERVICE")
	KubernetesServicesListSupplier kubernetesServicesListSupplier(Environment environment, CoreV1Api coreV1Api,
			KubernetesClientServiceInstanceMapper mapper, KubernetesDiscoveryProperties discoveryProperties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new KubernetesClientServicesListSupplier(environment, mapper, discoveryProperties, coreV1Api,
				kubernetesNamespaceProvider);
	}

}
