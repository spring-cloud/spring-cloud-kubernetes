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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author wind57
 */
class KubernetesDiscoveryClientServiceMetadataTests {

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataEmpty() {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("k8s_namespace", "default", "type", "ClusterIP"));
	}

	/**
	 * <pre>
	 *     - labels are added without a prefix
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsNoPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b")).build()).build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result, Map.of("a", "b", "k8s_namespace", "default", "type", "ClusterIP"));
		String labelsMetadata = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
			output.getOut().contains("Adding labels metadata: " + labelsMetadata + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are added with prefix
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsWithPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "prefix-";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b", "c", "d")).build()).build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
			Map.of("prefix-a", "b", "prefix-c", "d", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String labelsMetadata = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
			output.getOut().contains("Adding labels metadata: " + labelsMetadata + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are added without prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddAnnotationsNoPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = true;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb")).withLabels(Map.of("a", "b"))
				.build())
			.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result, Map.of("aa", "bb", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions
			.assertTrue(output.getOut().contains("Adding annotations metadata: {aa=bb} for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are added with prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddAnnotationsWithPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = true;
		String annotationsPrefix = "prefix-";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb", "cc", "dd"))
				.withLabels(Map.of("a", "b")).build())
			.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
			Map.of("prefix-aa", "bb", "prefix-cc", "dd", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String annotations = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
			output.getOut().contains("Adding annotations metadata: " + annotations + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are added with prefix
	 *     - annotations are added with prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsAndAnnotationsWithPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "label-";
		boolean addAnnotations = true;
		String annotationsPrefix = "annotation-";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
			labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).withMetadata(new ObjectMetaBuilder()
				.withAnnotations(Map.of("aa", "bb", "cc", "dd")).withLabels(Map.of("a", "b", "c", "d")).build())
			.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
			properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 6);
		Assertions.assertEquals(result, Map.of("annotation-aa", "bb", "annotation-cc", "dd", "label-a", "b", "label-c",
			"d", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String labels = result.entrySet().stream().filter(en -> en.getKey().contains("label"))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
		String annotations = result.entrySet().stream().filter(en -> en.getKey().contains("annotation"))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
		Assertions.assertTrue(
			output.getOut().contains("Adding labels metadata: " + labels + " for serviceId: my-service"));
		Assertions.assertTrue(
			output.getOut().contains("Adding annotations metadata: " + annotations + " for serviceId: my-service"));
	}

}
