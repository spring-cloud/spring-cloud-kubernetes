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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesDiscoveryClientServiceWithoutPortNameTests {

	private static final String NAMESPACE = "spring-k8s";

	private static KubernetesClient mockClient;

	@Test
	void testDiscoveryWithoutAServicePortName() {

		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withPorts(new ServicePortBuilder().withPort(8080).build()).build())
				.withMetadata(new ObjectMetaBuilder().withName("no-port-name-service").withNamespace(NAMESPACE).build())
				.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).build();
		mockClient.services().inNamespace(NAMESPACE).resource(service).create();

		Endpoints endpoints = new EndpointsBuilder()
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build())
						.withAddresses(new EndpointAddressBuilder().withIp("127.0.0.1").build()).build())
				.withMetadata(new ObjectMetaBuilder().withName("no-port-name-service").withNamespace(NAMESPACE).build())
				.build();
		mockClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(NAMESPACE),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties, a -> null);

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("no-port-name-service");
		Assertions.assertEquals(serviceInstances.size(), 1);
		Assertions.assertEquals(serviceInstances.get(0).getMetadata(),
				Map.of("port.<unset>", "8080", "k8s_namespace", "spring-k8s", "type", "ClusterIP"));
	}

}
