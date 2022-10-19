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

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
class KubernetesClientServiceInstanceMapperTests {

	@Test
	void basicMap() {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(loadBalancerProperties,
				KubernetesDiscoveryProperties.DEFAULT);

		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("database").withUid("0").withResourceVersion("0")
						.withNamespace("default").addToAnnotations("org.springframework.cloud", "true")
						.addToLabels("beta", "true").build())
				.withSpec(new V1ServiceSpecBuilder()
						.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build()).build())
				.build();

		KubernetesServiceInstance serviceInstance = mapper.map(service);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("org.springframework.cloud", "true");
		metadata.put("beta", "true");
		DefaultKubernetesServiceInstance result = new DefaultKubernetesServiceInstance("0", "database",
				"database.default.svc.cluster.local", 80, metadata, false);
		assertThat(serviceInstance).isEqualTo(result);
	}

	@Test
	void multiportMap() {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("https");
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(loadBalancerProperties,
				KubernetesDiscoveryProperties.DEFAULT);

		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("database").withUid("0").withResourceVersion("0")
						.withNamespace("default").build())
				.withSpec(new V1ServiceSpecBuilder()
						.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build(),
								new V1ServicePortBuilder().withPort(443).withName("https").build())
						.build())
				.build();

		KubernetesServiceInstance serviceInstance = mapper.map(service);
		DefaultKubernetesServiceInstance result = new DefaultKubernetesServiceInstance("0", "database",
				"database.default.svc.cluster.local", 443, new HashMap(), true);
		assertThat(serviceInstance).isEqualTo(result);
	}

}
