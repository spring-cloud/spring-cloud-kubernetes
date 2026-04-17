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

import java.util.List;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
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

	@BeforeAll
	static void beforeAllLocal() throws Exception {
		k8sNativeKubernetesFixture.createNamespace(NAMESPACE_LEFT);
		k8sNativeKubernetesFixture.createNamespace(NAMESPACE_RIGHT);

		Commons.validateImage(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(DISCOVERY_SERVER_APP_NAME, K3S);
		k8sNativeKubernetesFixture.setUp(NAMESPACE);
		serviceAccount(Phase.CREATE);
		discoveryServer(Phase.CREATE);

		Images.loadWiremock(K3S);
		k8sNativeKubernetesFixture.wiremock(NAMESPACE_LEFT, Phase.CREATE, false);
		k8sNativeKubernetesFixture.wiremock(NAMESPACE_RIGHT, Phase.CREATE, false);
	}

	@AfterAll
	static void afterAllLocal() {
		serviceAccount(Phase.DELETE);
		discoveryServer(Phase.DELETE);

		k8sNativeKubernetesFixture.wiremock(NAMESPACE_LEFT, Phase.DELETE, false);
		k8sNativeKubernetesFixture.wiremock(NAMESPACE_RIGHT, Phase.DELETE, false);

		k8sNativeKubernetesFixture.deleteNamespace(NAMESPACE_LEFT);
		k8sNativeKubernetesFixture.deleteNamespace(NAMESPACE_RIGHT);
	}

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
		assertThat(namespaces).containsExactlyInAnyOrder(NAMESPACE_LEFT, NAMESPACE_RIGHT);

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
		assertThat(namespaces).containsExactlyInAnyOrder(NAMESPACE_LEFT, NAMESPACE_RIGHT);
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(Set.of(NAMESPACE_LEFT, NAMESPACE_RIGHT));
	}

}
