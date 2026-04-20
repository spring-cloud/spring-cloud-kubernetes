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

package org.springframework.cloud.kubernetes.discoveryclient.it;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(classes = { App.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
		"logging.level.org.springframework.cloud.kubernetes.discovery=debug",
		"spring.cloud.kubernetes.discovery.catalogServicesWatchDelay=3000",
		"spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=true" })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@NativeClientIntegrationTest(
	namespaces = { "left", "right" },
	wiremock = @NativeClientIntegrationTest.Wiremock(enabled = true, namespaces = {"left", "right"}, withNodePort = false),
	withImages = "spring-cloud-kubernetes-discoveryserver", rbacNamespaces = "default",
	clusterWideRBAC = @NativeClientIntegrationTest.ClusterWideRBAC(enabled = true, serviceAccountNamespace = "default",
	roleBindingNamespaces = { "left", "right" }),
	deployDiscoverServer = true)
class DiscoveryServerClientIT extends DiscoveryServerClientBase {

	@TestBean
	private ApiClient apiClient;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	@Autowired
	private DiscoveryClient discoveryClient;

	@Autowired
	private ReactiveDiscoveryClient reactiveDiscoveryClient;

	@Autowired
	private HeartbeatListener heartbeatListener;

	/**
	 * <pre>
	 *     - searching is enabled in two namespaces : left and right
	 * </pre>
	 */
	@Test
	@Order(1)
	void testBlocking(CapturedOutput output) {
		List<String> services = reactiveDiscoveryClient.getServices().collectList().block();
		assertThat(services).hasSize(1);
		assertThat(services).contains("service-wiremock");

		List<ServiceInstance> serviceInstances = reactiveDiscoveryClient.getInstances("service-wiremock")
			.collectList()
			.block();
		List<DefaultKubernetesServiceInstance> defaultKubernetesServiceInstances = serviceInstances.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.toList();
		assertThat(defaultKubernetesServiceInstances).hasSize(2);

		List<String> namespaces = defaultKubernetesServiceInstances.stream()
			.map(DefaultKubernetesServiceInstance::getNamespace)
			.toList();
		assertThat(namespaces).containsExactlyInAnyOrder("left", "right");

		testHeartBeat(heartbeatListener, output);
	}

	/**
	 * <pre>
	 *     - searching is enabled in two namespaces : left and right
	 * </pre>
	 */
	@Test
	@Order(2)
	void testReactive() {
		List<String> services = discoveryClient.getServices();
		assertThat(services).hasSize(1);
		assertThat(services).contains("service-wiremock");

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock");
		List<DefaultKubernetesServiceInstance> defaultKubernetesServiceInstances = serviceInstances.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.toList();
		assertThat(defaultKubernetesServiceInstances).hasSize(2);

		List<String> namespaces = defaultKubernetesServiceInstances.stream()
			.map(DefaultKubernetesServiceInstance::getNamespace)
			.toList();
		assertThat(namespaces).containsExactlyInAnyOrder("left", "right");
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(Set.of("left", "right"));
	}

	private static ApiClient apiClient() {
		String kubeConfigYaml = K3S.getKubeConfigYaml();

		ApiClient client;
		try {
			client = Config.fromConfig(new StringReader(kubeConfigYaml));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new CoreV1Api(client).getApiClient();
	}

}
