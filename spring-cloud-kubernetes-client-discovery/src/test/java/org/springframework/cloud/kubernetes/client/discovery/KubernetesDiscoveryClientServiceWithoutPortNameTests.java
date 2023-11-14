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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubsetBuilder;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
class KubernetesDiscoveryClientServiceWithoutPortNameTests {

	private static final String NAMESPACE = "spring-k8s";

	private static final SharedInformerFactoryStub STUB = new SharedInformerFactoryStub();

	private static final SharedInformerStub<V1Service> SERVICE_SHARED_INFORMER_STUB = new SharedInformerStub<>();

	private static final SharedInformerStub<V1Endpoints> ENDPOINTS_SHARED_INFORMER_STUB = new SharedInformerStub<>();

	private Cache<V1Service> servicesCache;

	private Lister<V1Service> servicesLister;

	private Cache<V1Endpoints> endpointsCache;

	private Lister<V1Endpoints> endpointsLister;

	@BeforeEach
	void beforeEach() {
		servicesCache = new Cache<>();
		servicesLister = new Lister<>(servicesCache);

		endpointsCache = new Cache<>();
		endpointsLister = new Lister<>(endpointsCache);
	}

	@Test
	void testDiscoveryWithoutAServicePortName() {

		V1Endpoints endpoints = new V1EndpointsBuilder()
				.withSubsets(
						new V1EndpointSubsetBuilder().withPorts(new CoreV1EndpointPortBuilder().withPort(8080).build())
								.withAddresses(new V1EndpointAddressBuilder().withIp("127.0.0.1").build()).build())
				.withMetadata(
						new V1ObjectMetaBuilder().withName("no-port-name-service").withNamespace(NAMESPACE).build())
				.build();
		endpointsCache.add(endpoints);

		V1Service service = new V1ServiceBuilder()
				.withSpec(
						new V1ServiceSpecBuilder().withPorts(new V1ServicePortBuilder().withPort(8080).build()).build())
				.withMetadata(
						new V1ObjectMetaBuilder().withName("no-port-name-service").withNamespace(NAMESPACE).build())
				.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP").build()).build();
		servicesCache.add(service);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(NAMESPACE),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("no-port-name-service");
		Assertions.assertEquals(serviceInstances.size(), 1);
		Assertions.assertEquals(serviceInstances.get(0).getMetadata(),
				Map.of("port.<unset>", "8080", "k8s_namespace", "spring-k8s", "type", "ClusterIP"));
	}

}
