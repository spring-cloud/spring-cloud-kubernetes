/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.discovery.it;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;

import static java.util.AbstractMap.SimpleEntry;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
final class TestAssertions {

	private TestAssertions() {

	}

	static void assertPodMetadata(DiscoveryClient discoveryClient) {

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("busybox-service");

		// if annotations are empty, we got the other pod, with labels here
		DefaultKubernetesServiceInstance withCustomLabel = serviceInstances.stream()
			.map(instance -> (DefaultKubernetesServiceInstance) instance)
			.filter(x -> x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		List<Entry<String, String>> podMetadataLabels = withCustomLabel.podMetadata()
				.get("labels")
				.entrySet()
				.stream()
				.toList();

		assertThat(withCustomLabel.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomLabel.getInstanceId()).isNotNull();
		assertThat(withCustomLabel.getHost()).isNotNull();
		assertThat(withCustomLabel.getMetadata()).isEqualTo(
			Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80")
		);
		assertThat(podMetadataLabels).contains(new SimpleEntry<>("my-label", "my-value"));

		// if annotation are present, we got the one with annotations here
		DefaultKubernetesServiceInstance withCustomAnnotation = serviceInstances.stream()
			.map(instance -> (DefaultKubernetesServiceInstance) instance)
			.filter(x -> !x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		List<Entry<String, String>> podMetadataAnnotations = withCustomAnnotation.podMetadata()
			.get("annotations")
			.entrySet()
			.stream()
			.toList();

		assertThat(withCustomLabel.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomLabel.getInstanceId()).isNotNull();
		assertThat(withCustomLabel.getHost()).isNotNull();
		assertThat(withCustomLabel.getMetadata()).isEqualTo(
			Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80")
		);
		assertThat(podMetadataAnnotations).contains(new SimpleEntry<>("my-annotation", "my-value"));
	}

	static void assertAllServices(DiscoveryClient discoveryClient) {

		List<String> services = discoveryClient.getServices();
		assertThat(services).containsExactlyInAnyOrder("kubernetes", "busybox-service", "external-name-service");

		ServiceInstance externalNameInstance = discoveryClient.getInstances("external-name-service").get(0);

		assertThat(externalNameInstance.getServiceId()).isEqualTo("external-name-service");
		assertThat(externalNameInstance.getInstanceId()).isNotNull();
		assertThat(externalNameInstance.getHost()).isEqualTo("spring.io");
		assertThat(externalNameInstance.getPort()).isEqualTo(-1);
		assertThat(externalNameInstance.getMetadata()).isEqualTo(
			Map.of("k8s_namespace", "default", "type", "ExternalName"));
		assertThat(externalNameInstance.isSecure()).isFalse();
		assertThat(externalNameInstance.getUri().toASCIIString()).isEqualTo("spring.io");
		assertThat(externalNameInstance.getScheme()).isEqualTo("http");
	}

}
