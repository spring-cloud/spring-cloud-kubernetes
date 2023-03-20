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

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.podMetadata;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
@EnableKubernetesMockClient(https = false, crud = true)
class KubernetesDiscoveryClientUtilsPodMetadataTests {

	private KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.pods().inAnyNamespace().delete();
	}

	/**
	 * service is of type ExternalName, thus no podMetadata is added.
	 */
	@Test
	void testExternalName() {
		Map<String, String> serviceMetadata = Map.of("type", "ExternalName");
		boolean addPodLabels = true;
		boolean addPodAnnotations = true;
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder().build();
		String namespace = "default";

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertEquals(result, Map.of());
	}

	/**
	 * service is not of type ExternalName, but neither podLabels nor podAnnotations are
	 * requested. As such, podMetadata is empty.
	 */
	@Test
	void testNotExternalName() {
		Map<String, String> serviceMetadata = Map.of("type", "ClusterIP");
		boolean addPodLabels = false;
		boolean addPodAnnotations = false;
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder().build();
		String namespace = "default";

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertEquals(result, Map.of());
	}

	/**
	 * service is not of type ExternalName, and podLabels are requested, but a pod does
	 * not exist. As such pod metadata is empty.
	 */
	@Test
	void testNotExternalPodNotPresent() {
		Map<String, String> serviceMetadata = Map.of("type", "ClusterIP");
		boolean addPodLabels = true;
		boolean addPodAnnotations = false;
		String podName = "my-pod";
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName(podName).build()).build();
		String namespace = "default";

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertEquals(result, Map.of());
	}

	/**
	 * service is not of type ExternalName, and podLabels are requested. As such,
	 * podMetadata contains only pod labels.
	 */
	@Test
	void testNotExternalNamePodLabelsRequested(CapturedOutput output) {
		Map<String, String> serviceMetadata = Map.of("type", "ClusterIP");
		boolean addPodLabels = true;
		boolean addPodAnnotations = false;
		String podName = "my-pod";
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName(podName).build()).build();
		String namespace = "default";

		client.pods().inNamespace(namespace)
				.resource(new PodBuilder().withNewMetadata().withName(podName)
						.withLabels(Map.of("label-key", "label-value"))
						.withAnnotations(Map.of("annotation-key", "annotation-value")).and().build())
				.create();

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertEquals(result.get("labels"), Map.of("label-key", "label-value"));
		Assertions.assertNull(result.get("annotations"));
		Assertions.assertTrue(
				output.getOut().contains("adding podMetadata : {labels={label-key=label-value}} from pod : my-pod"));
	}

	/**
	 * service is not of type ExternalName, and podAnnotations are requested. As such,
	 * podMetadata contains only pod annotations.
	 */
	@Test
	void testNotExternalNamePodAnnotationsRequested(CapturedOutput output) {
		Map<String, String> serviceMetadata = Map.of("type", "ClusterIP");
		boolean addPodLabels = false;
		boolean addPodAnnotations = true;
		String podName = "my-pod";
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName(podName).build()).build();
		String namespace = "default";

		client.pods().inNamespace(namespace)
				.resource(new PodBuilder().withNewMetadata().withName(podName)
						.withLabels(Map.of("label-key", "label-value"))
						.withAnnotations(Map.of("annotation-key", "annotation-value")).and().build())
				.create();

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertNull(result.get("labels"));
		Assertions.assertEquals(result.get("annotations"), Map.of("annotation-key", "annotation-value"));
		Assertions.assertTrue(output.getOut()
				.contains("adding podMetadata : {annotations={annotation-key=annotation-value}} from pod : my-pod"));
	}

	/**
	 * service is not of type ExternalName, both podLabels and podAnnotations are
	 * requested. As such, podMetadata contains both.
	 */
	@Test
	void testNotExternalNamePodLabelsAndAnnotationsRequested(CapturedOutput output) {
		Map<String, String> serviceMetadata = Map.of("type", "ClusterIP");
		boolean addPodLabels = true;
		boolean addPodAnnotations = true;
		String podName = "my-pod";
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, "", false,
				"", false, "", addPodLabels, addPodAnnotations);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName(podName).build()).build();
		String namespace = "default";

		client.pods().inNamespace(namespace)
				.resource(new PodBuilder().withNewMetadata().withName(podName)
						.withLabels(Map.of("label-key", "label-value"))
						.withAnnotations(Map.of("annotation-key", "annotation-value")).and().build())
				.create();

		Map<String, Map<String, String>> result = podMetadata(client, serviceMetadata, properties, endpointAddress,
				namespace);
		Assertions.assertEquals(result.get("labels"), Map.of("label-key", "label-value"));
		Assertions.assertEquals(result.get("annotations"), Map.of("annotation-key", "annotation-value"));
		Assertions.assertTrue(output.getOut().contains(
				"adding podMetadata : {annotations={annotation-key=annotation-value}, labels={label-key=label-value}} from pod : my-pod"));
	}

}
