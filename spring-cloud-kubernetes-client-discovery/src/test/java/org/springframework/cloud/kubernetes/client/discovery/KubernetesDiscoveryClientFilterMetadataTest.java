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
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.assertj.core.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesDiscoveryClientFilterMetadataTest {

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
	void testAllExtraMetadataDisabled() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, false,
				null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "lab"), Map.of("l1", "lab"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).isEqualTo(Map.of("k8s_namespace", "ns", "type", "ClusterIP"));
	}

	@Test
	void testLabelsEnabled() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, null, false,
				null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "v1", "l2", "v2"), Map.of("l1", "lab"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l1", "v1"), entry("l2", "v2"),
				entry("k8s_namespace", "ns"), entry("type", "ClusterIP"));
	}

	@Test
	void testLabelsEnabledWithPrefix() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, "l_", false,
				null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "v1", "l2", "v2"), Map.of("l1", "lab"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l_l1", "v1"), entry("l_l2", "v2"),
				entry("k8s_namespace", "ns"), entry("type", "ClusterIP"));
	}

	@Test
	void testAnnotationsEnabled() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, true,
				null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "v1"), Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a1", "v1"), entry("a2", "v2"),
				entry("k8s_namespace", "ns"), entry("type", "ClusterIP"));
	}

	@Test
	void testAnnotationsEnabledWithPrefix() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, true,
				"a_", false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "v1"), Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "v1"), entry("a_a2", "v2"),
				entry("k8s_namespace", "ns"), entry("type", "ClusterIP"));
	}

	@Test
	void testPortsEnabled() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, false,
				null, true, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "test", Map.of("l1", "v1"), Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("http", "80"), entry("k8s_namespace", "test"),
				entry("<unset>", "5555"), entry("type", "ClusterIP"));
	}

	@Test
	void testPortsEnabledWithPrefix() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, false,
				null, true, "p_");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "v1"), Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("p_http", "80"), entry("k8s_namespace", "ns"),
				entry("p_<unset>", "5555"), entry("type", "ClusterIP"));
	}

	@Test
	void testLabelsAndAnnotationsAndPortsEnabledWithPrefix() {
		String serviceId = "s";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, "l_", true,
				"a_", true, "p_");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		setup(serviceId, "ns", Map.of("l1", "la1"), Map.of("a1", "an1", "a2", "an2"), Map.of(80, "http", 5555, ""));

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "an1"), entry("a_a2", "an2"),
				entry("l_l1", "la1"), entry("p_http", "80"), entry("k8s_namespace", "ns"), entry("type", "ClusterIP"),
				entry("p_<unset>", "5555"));
	}

	private void setup(String serviceId, String namespace, Map<String, String> labels, Map<String, String> annotations,
			Map<Integer, String> ports) {

		V1Service service = new V1ServiceBuilder()
				.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP").withPorts(getServicePorts(ports)).build())
				.withNewMetadata().withName(serviceId).withNamespace(namespace).withLabels(labels)
				.withAnnotations(annotations).endMetadata().build();

		servicesCache.add(service);

		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setNamespace(namespace);
		objectMeta.setName(serviceId);

		V1Endpoints endpoints = new V1EndpointsBuilder().withMetadata(objectMeta).addNewSubset()
				.addAllToPorts(getEndpointPorts(ports)).addNewAddress().endAddress().endSubset().build();

		endpointsCache.add(endpoints);

	}

	private List<V1ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			V1ServicePortBuilder servicePortBuilder = new V1ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

	private List<CoreV1EndpointPort> getEndpointPorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			CoreV1EndpointPortBuilder endpointPortBuilder = new CoreV1EndpointPortBuilder();
			endpointPortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				endpointPortBuilder.withName(e.getValue());
			}
			return endpointPortBuilder.build();
		}).collect(toList());
	}

}
