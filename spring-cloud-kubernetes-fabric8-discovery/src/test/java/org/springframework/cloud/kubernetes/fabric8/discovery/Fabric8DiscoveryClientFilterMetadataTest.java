/*
 * Copyright 2013-present the original author or authors.
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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryClientFilterMetadataTest extends Fabric8DiscoveryClientBase {

	private static final String NAMESPACE = "test";

	private static KubernetesClient mockClient;

	@AfterEach
	void afterEach() {
		mockClient.services().inAnyNamespace().delete();
		mockClient.endpoints().inAnyNamespace().delete();
	}

	@Test
	void testAllExtraMetadataDisabled() {
		String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of("label1", "one"), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("annotation1", "ann-one"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE),
				mockClient);

		List<ServiceInstance> instances = fabric8DiscoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).isEqualTo(Map.of("k8s_namespace", NAMESPACE, "type", "ClusterIP"));
	}

	@Test
	void testLabelsEnabled() {
		String serviceId = "s";

		Metadata metadata = new Metadata(true, null, true, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("annotation1", "ann-one"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE),
				mockClient);

		List<ServiceInstance> instances = fabric8DiscoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("label1", "one"), entry("annotation1", "ann-one"),
				entry("k8s_namespace", "test"), entry("type", "ClusterIP"));
	}

	@Test
	void testLabelsEnabledWithPrefix() {
		String serviceId = "s";

		Metadata metadata = new Metadata(true, "l_", false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE,
				Map.of("label1", "one", "label2", "two"), Map.of("annotation1", "ann-one"),
				Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l_label1", "one"), entry("l_label2", "two"),
				entry("k8s_namespace", NAMESPACE), entry("type", "ClusterIP"));
	}

	@Test
	void testAnnotationsEnabled() {
		String serviceId = "s";

		Metadata metadata = new Metadata(false, null, true, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a1", "v1"), entry("a2", "v2"),
				entry("k8s_namespace", NAMESPACE), entry("type", "ClusterIP"));
	}

	@Test
	void testAnnotationsEnabledWithPrefix() {
		String serviceId = "s";

		Metadata metadata = new Metadata(false, null, true, "a_", false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "v1"), entry("a_a2", "v2"),
				entry("k8s_namespace", NAMESPACE), entry("type", "ClusterIP"));
	}

	@Test
	void testPortsEnabled() {
		String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, true, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("http", "80"), entry("k8s_namespace", "test"),
				entry("<unset>", "5555"), entry("type", "ClusterIP"));
	}

	@Test
	void testPortsEnabledWithPrefix() {
		String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, true, "p_");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("a1", "v1", "a2", "v2"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("p_http", "80"),
				entry("k8s_namespace", NAMESPACE), entry("p_<unset>", "5555"), entry("type", "ClusterIP"));
	}

	@Test
	void testLabelsAndAnnotationsAndPortsEnabledWithPrefix() {
		String serviceId = "s";

		Metadata metadata = new Metadata(true, "l_", true, "a_", true, "p_");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		setupServiceWithLabelsAndAnnotationsAndPorts(mockClient, serviceId, NAMESPACE, Map.of("label1", "one"),
				Map.of("a1", "an1", "a2", "an2"), Map.of(80, "http", 5555, ""));

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "an1"), entry("a_a2", "an2"),
				entry("l_label1", "one"), entry("p_http", "80"), entry("k8s_namespace", NAMESPACE),
				entry("type", "ClusterIP"), entry("p_<unset>", "5555"));
	}

}
