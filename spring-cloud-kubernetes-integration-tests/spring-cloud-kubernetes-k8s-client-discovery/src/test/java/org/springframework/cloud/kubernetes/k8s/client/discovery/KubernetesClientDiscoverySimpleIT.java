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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ExternalNameKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.k8s.client.discovery.TestAssertions.assertLogStatement;

/**
 * @author wind57
 */
@SpringBootTest(classes = { DiscoveryApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.namespaces[0]=default",
		"org.springframework.cloud.kubernetes.client.discovery=debug" })
@NativeClientIntegrationTest(busyboxNamespaces = "default", deployExternalNameService = true)
class KubernetesClientDiscoverySimpleIT extends KubernetesClientDiscoveryBase {

	@TestBean
	private ApiClient apiClient;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	@Autowired
	private DiscoveryClient discoveryClient;

	@Test
	void test(CapturedOutput output, K3sContainer container) throws Exception {

		// find both pods
		String[] both = container.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
			.getStdout()
			.split("\n");
		// add a label to first pod
		container.execInContainer("sh", "-c",
				"kubectl label pods " + both[0].split("/")[1] + " custom-label=custom-label-value");

		// add annotation to the second pod
		container.execInContainer("sh", "-c",
				"kubectl annotate pods " + both[1].split("/")[1] + " custom-annotation=custom-annotation-value");

		assertLogStatement(output, "serviceSharedInformers will use selective namespaces : [default]");

		List<String> services = discoveryClient.getServices();
		List<ServiceInstance> instances = discoveryClient.getInstances("busybox-service");

		Assertions.assertThat(services)
			.containsExactlyInAnyOrder("kubernetes", "busybox-service", "external-name-service");
		testCustomLabel(instances);
		testCustomAnnotation(instances);
		testUnExistentService(discoveryClient);
		testExternalNameService(discoveryClient);
	}

	// pod where annotations are not present
	private void testCustomLabel(List<ServiceInstance> instances) {
		DefaultKubernetesServiceInstance withCustomLabel = instances.stream()
			.map(serviceInstance -> (DefaultKubernetesServiceInstance) serviceInstance)
			.filter(x -> x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		assertThat(withCustomLabel.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomLabel.getInstanceId()).isNotNull();
		assertThat(withCustomLabel.getHost()).isNotNull();
		assertThat(withCustomLabel.getMetadata())
			.containsAllEntriesOf(Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
	}

	// pod where annotations are present
	private void testCustomAnnotation(List<ServiceInstance> instances) {
		DefaultKubernetesServiceInstance withCustomAnnotation = instances.stream()
			.map(serviceInstance -> (DefaultKubernetesServiceInstance) serviceInstance)
			.filter(x -> !x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		assertThat(withCustomAnnotation.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomAnnotation.getInstanceId()).isNotNull();
		assertThat(withCustomAnnotation.getHost()).isNotNull();
		assertThat(withCustomAnnotation.getMetadata())
			.containsAllEntriesOf(Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));

		Map<String, String> annotations = withCustomAnnotation.podMetadata().get("annotations");
		assertThat(annotations).containsEntry("custom-annotation", "custom-annotation-value");
	}

	private void testExternalNameService(DiscoveryClient discoveryClient) {
		ExternalNameKubernetesServiceInstance externalNameService = (ExternalNameKubernetesServiceInstance) discoveryClient
			.getInstances("external-name-service")
			.get(0);

		assertThat(externalNameService.getInstanceId()).isNotNull();
		assertThat(externalNameService.getHost()).isEqualTo("spring.io");
		assertThat(externalNameService.getPort()).isEqualTo(-1);
		assertThat(externalNameService.getMetadata())
			.containsAllEntriesOf(Map.of("k8s_namespace", "default", "type", "ExternalName"));
		assertThat(externalNameService.isSecure()).isFalse();
		assertThat(externalNameService.getUri().toASCIIString()).isEqualTo("spring.io");
		assertThat(externalNameService.getScheme()).isNull();
	}

	// https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1286
	private void testUnExistentService(DiscoveryClient discoveryClient) {
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("non-existent");
		assertThat(serviceInstances).isEmpty();
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(false, Set.of(DEFAULT_NAMESPACE), null, Map.of());
	}

}
