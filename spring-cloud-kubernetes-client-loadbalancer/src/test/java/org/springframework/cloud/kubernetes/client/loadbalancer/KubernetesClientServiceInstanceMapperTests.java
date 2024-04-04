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

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePort;
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
	void singlePortNonSecure() {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(loadBalancerProperties,
				KubernetesDiscoveryProperties.DEFAULT);

		Map<String, String> annotations = Map.of("org.springframework.cloud", "true");
		Map<String, String> labels = Map.of("beta", "true");
		List<V1ServicePort> servicePorts = List.of(
			new V1ServicePortBuilder().withName("http").withPort(80).build()
		);
		V1Service service = createService("database", "default", annotations, labels, servicePorts);

		KubernetesServiceInstance serviceInstance = mapper.map(service);
		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true",
			"k8s_namespace", "default", "type", "V1Service");
		DefaultKubernetesServiceInstance result = new DefaultKubernetesServiceInstance("0", "database",
				"database.default.svc.cluster.local", 80, metadata, false);
		assertThat(serviceInstance).isEqualTo(result);
	}

	// has an annotation 'secured=true'
	@Test
	void singlePortSecure() {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(loadBalancerProperties,
			KubernetesDiscoveryProperties.DEFAULT);

		Map<String, String> annotations = Map.of("org.springframework.cloud", "true", "secured", "true");
		Map<String, String> labels = Map.of("beta", "true");
		List<V1ServicePort> servicePorts = List.of(
			new V1ServicePortBuilder().withName("http").withPort(80).build()
		);
		V1Service service = createService("database", "default", annotations, labels, servicePorts);

		KubernetesServiceInstance serviceInstance = mapper.map(service);
		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true", "secured", "true",
			"k8s_namespace", "default", "type", "V1Service");
		DefaultKubernetesServiceInstance result = new DefaultKubernetesServiceInstance("0", "database",
			"database.default.svc.cluster.local", 80, metadata, true);
		assertThat(serviceInstance).isEqualTo(result);
	}

	@Test
	void multiplePortsSecure() {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("https");
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(loadBalancerProperties,
				KubernetesDiscoveryProperties.DEFAULT);

		Map<String, String> annotations = Map.of("org.springframework.cloud", "true");
		Map<String, String> labels = Map.of("beta", "true");
		List<V1ServicePort> servicePorts = List.of(
			new V1ServicePortBuilder().withName("http").withPort(80).build(),
			new V1ServicePortBuilder().withName("https").withPort(443).build()
		);
		V1Service service = createService("database", "default", annotations, labels, servicePorts);

		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true",
			"k8s_namespace", "default", "type", "V1Service");
		KubernetesServiceInstance serviceInstance = mapper.map(service);
		DefaultKubernetesServiceInstance result = new DefaultKubernetesServiceInstance("0", "database",
				"database.default.svc.cluster.local", 443, metadata, true);
		assertThat(serviceInstance).isEqualTo(result);
	}

	private V1Service createService(String name, String namespace, Map<String, String> annotations,
			Map<String, String> labels, List<V1ServicePort> servicePorts) {
		return new V1ServiceBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(name).withUid("0")
				.withNamespace(namespace).addToAnnotations(annotations)
				.addToLabels(labels).build())
			.withSpec(new V1ServiceSpecBuilder().addAllToPorts(servicePorts).withType("V1Service").build()).build();
	}

}
