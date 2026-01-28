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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryClientServiceLabelsFilteringTests
		extends KubernetesClientDiscoveryClientServiceLabelsFiltering {

	@Test
	void namespaceARedLabels() {

		Map<String, String> labels = Map.of("color", "red");
		List<String> namespaces = List.of("namespaceA");
		Set<String> namespacesAsSet = Set.of("namespaceA");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceX");
		// only the service with { namespace=a } is returned
		assertThat(serviceInstances).hasSize(1);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstances.get(0).getMetadata().get("namespace")).isEqualTo("a");

		assertThat(discoveryClient.getInstances("serviceXX")).isEmpty();
	}

	@Test
	void namespaceAGreenLabels() {

		Map<String, String> labels = Map.of("color", "green");
		List<String> namespaces = List.of("namespaceA");
		Set<String> namespacesAsSet = Set.of("namespaceA");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceXX");
		// only the service with { namespace=a } is returned
		assertThat(serviceInstances).hasSize(1);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstances.get(0).getMetadata().get("namespace")).isEqualTo("a");

		assertThat(discoveryClient.getInstances("serviceX")).isEmpty();
	}

	@Test
	void namespaceANoLabels() {

		Map<String, String> labels = Map.of();
		List<String> namespaces = List.of("namespaceA");
		Set<String> namespacesAsSet = Set.of("namespaceA");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstancesA = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesA).hasSize(1);
		assertThat(serviceInstancesA.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesA.get(0).getMetadata().get("namespace")).isEqualTo("a");

		List<ServiceInstance> serviceInstancesAA = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesAA).hasSize(1);
		assertThat(serviceInstancesAA.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesAA.get(0).getMetadata().get("namespace")).isEqualTo("a");
	}

	@Test
	void namespaceANullLabels() {

		Map<String, String> labels = null;
		List<String> namespaces = List.of("namespaceA");
		Set<String> namespacesAsSet = Set.of("namespaceA");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(1);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(0).getMetadata().get("namespace")).isEqualTo("a");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(1);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(0).getMetadata().get("namespace")).isEqualTo("a");
	}

	@Test
	void namespaceBRedLabels() {

		Map<String, String> labels = Map.of("color", "red");
		List<String> namespaces = List.of("namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceX");
		// only the service with { namespace=b } is returned
		assertThat(serviceInstances).hasSize(1);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstances.get(0).getMetadata().get("namespace")).isEqualTo("b");

		assertThat(discoveryClient.getInstances("serviceXX")).isEmpty();
	}

	@Test
	void namespaceBGreenLabels() {

		Map<String, String> labels = Map.of("color", "green");
		List<String> namespaces = List.of("namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceXX");
		// only the service with { namespace=b } is returned
		assertThat(serviceInstances).hasSize(1);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstances.get(0).getMetadata().get("namespace")).isEqualTo("b");

		assertThat(discoveryClient.getInstances("serviceX")).isEmpty();
	}

	@Test
	void namespaceBNoLabels() {

		Map<String, String> labels = Map.of();
		List<String> namespaces = List.of("namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(1);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(0).getMetadata().get("namespace")).isEqualTo("b");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(1);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(0).getMetadata().get("namespace")).isEqualTo("b");
	}

	@Test
	void namespaceBNullLabels() {

		Map<String, String> labels = null;
		List<String> namespaces = List.of("namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(1);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(0).getMetadata().get("namespace")).isEqualTo("b");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(1);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(0).getMetadata().get("namespace")).isEqualTo("b");
	}

	@Test
	void namespaceAndBRedLabels() {

		Map<String, String> labels = Map.of("color", "red");
		List<String> namespaces = List.of("namespaceA", "namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceA", "namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstances).hasSize(2);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstances.get(1).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstances.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");
	}

	@Test
	void namespaceAndBGreenLabels() {

		Map<String, String> labels = Map.of("color", "green");
		List<String> namespaces = List.of("namespaceA", "namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceA", "namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstances).hasSize(2);
		assertThat(serviceInstances.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstances.get(1).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstances.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");
	}

	@Test
	void namespaceAndBNoLabels() {

		Map<String, String> labels = Map.of();
		List<String> namespaces = List.of("namespaceA", "namespaceB");
		Set<String> namespacesAsSet = Set.of("namespaceA", "namespaceB");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);

		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(2);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(1).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(2);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(1).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

	}

	@Test
	void allNamespacesRedLabel() {

		Map<String, String> labels = Map.of("color", "red");
		// this simulates NAMESPACES_ALL
		List<String> namespaces = List.of("");
		Set<String> namespacesAsSet = Set.of("");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);

		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(2);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(1).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(0);

	}

	@Test
	void allNamespacesGreenLabel() {

		Map<String, String> labels = Map.of("color", "green");
		// this simulates NAMESPACES_ALL
		List<String> namespaces = List.of("");
		Set<String> namespacesAsSet = Set.of("");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);

		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(0);

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(2);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(1).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

	}

	@Test
	void allNamespacesNoLabels() {

		Map<String, String> labels = Map.of();
		// this simulates NAMESPACES_ALL
		List<String> namespaces = List.of("");
		Set<String> namespacesAsSet = Set.of("");
		boolean discoveryInAllNamespaces = false;

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(false,
				discoveryInAllNamespaces, namespacesAsSet, true, 60L, false, null, Set.of(), labels, null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerDiscoveryClient discoveryClient = createAndStartListers(namespaces,
				discoveryProperties);

		List<ServiceInstance> serviceInstancesX = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstancesX).hasSize(2);
		assertThat(serviceInstancesX.get(0).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.get(1).getMetadata().get("color")).isEqualTo("red");
		assertThat(serviceInstancesX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

		List<ServiceInstance> serviceInstancesXX = discoveryClient.getInstances("serviceXX");
		assertThat(serviceInstancesXX).hasSize(2);
		assertThat(serviceInstancesXX.get(0).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.get(1).getMetadata().get("color")).isEqualTo("green");
		assertThat(serviceInstancesXX.stream().map(x -> x.getMetadata().get("namespace")).toList())
			.containsExactlyInAnyOrder("a", "b");

	}

}
