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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.k8s.client.discovery.TestAssertions.assertBlockingConfiguration;

/**
 * @author wind57
 */
@SpringBootTest(classes = { DiscoveryApp.class, KubernetesClientBlockingIT.TestConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesClientBlockingIT extends KubernetesClientDiscoveryBase {

	@LocalManagementPort
	private int port;

	@Autowired
	private DiscoveryClient discoveryClient;

	@BeforeAll
	static void beforeAllLocal() {

		util.createNamespace(NON_DEFAULT_NAMESPACE);

		Images.loadWiremock(K3S);
		Images.loadBusybox(K3S);
		util.wiremock(DEFAULT_NAMESPACE, Phase.CREATE, false);
		util.busybox(NON_DEFAULT_NAMESPACE, Phase.CREATE);
	}

	@AfterAll
	static void afterAllLocal() {
		util.wiremock(DEFAULT_NAMESPACE, Phase.DELETE, false);
		util.busybox(NON_DEFAULT_NAMESPACE, Phase.DELETE);
		util.deleteNamespace(NON_DEFAULT_NAMESPACE);
	}

	private void assertAllNamespacesAllLabels(DiscoveryClient discoveryClient) {

		List<ServiceInstance> wiremockServiceInstances = discoveryClient.getInstances("service-wiremock");
		assertThat(wiremockServiceInstances).hasSize(1);
		DefaultKubernetesServiceInstance wiremockService = (DefaultKubernetesServiceInstance) wiremockServiceInstances
			.get(0);
		assertThat(wiremockService.getServiceId()).isEqualTo("service-wiremock");
		assertThat(wiremockService.getInstanceId()).isNotNull();
		assertThat(wiremockService.getHost()).isNotNull();
		assertThat(wiremockService.getMetadata()).isEqualTo(Map.of("k8s_namespace", "default", "type", "ClusterIP",
				"port.http", "8080", "app", "service-wiremock"));

		List<ServiceInstance> busyboxServiceInstances = discoveryClient.getInstances("busybox-service");
		assertThat(busyboxServiceInstances).hasSize(2);
		DefaultKubernetesServiceInstance busyboxService = (DefaultKubernetesServiceInstance) busyboxServiceInstances
			.get(0);
		assertThat(busyboxService.getServiceId()).isEqualTo("busybox-service");
		assertThat(busyboxService.getInstanceId()).isNotNull();
		assertThat(busyboxService.getHost()).isNotNull();
		assertThat(busyboxService.getMetadata()).isEqualTo(Map.of("k8s_namespace", "non-default", "type", "ClusterIP",
				"port.busybox-port", "80", "app", "service-busybox"));

	}

	private void assertAllNamespacesWiremockLabels(DiscoveryClient discoveryClient) {

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock");
		assertThat(serviceInstances).hasSize(1);
		List<DefaultKubernetesServiceInstance> defaultKubernetesServiceInstances = serviceInstances.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.sorted(Comparator.comparing(x -> x.getMetadata().get("k8s_namespace")))
			.toList();

		DefaultKubernetesServiceInstance defaultKubernetesServiceInstance = defaultKubernetesServiceInstances.get(0);

		assertThat(defaultKubernetesServiceInstance.getServiceId()).isEqualTo("service-wiremock");
		assertThat(defaultKubernetesServiceInstance.getInstanceId()).isNotNull();
		assertThat(defaultKubernetesServiceInstance.getHost()).isNotNull();
		assertThat(defaultKubernetesServiceInstance.getMetadata()).isEqualTo(Map.of("k8s_namespace", "default", "type",
				"ClusterIP", "port.http", "8080", "app", "service-wiremock"));

	}

	private void assertDefaultNamespaceAllLabels(DiscoveryClient discoveryClient) {

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock");
		assertThat(serviceInstances).hasSize(1);
		List<DefaultKubernetesServiceInstance> defaultKubernetesServiceInstances = serviceInstances.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.sorted(Comparator.comparing(x -> x.getMetadata().get("k8s_namespace")))
			.toList();

		DefaultKubernetesServiceInstance defaultKubernetesServiceInstance = defaultKubernetesServiceInstances.get(0);

		assertThat(defaultKubernetesServiceInstance.getServiceId()).isEqualTo("service-wiremock");
		assertThat(defaultKubernetesServiceInstance.getInstanceId()).isNotNull();
		assertThat(defaultKubernetesServiceInstance.getHost()).isNotNull();
		assertThat(defaultKubernetesServiceInstance.getMetadata()).isEqualTo(Map.of("k8s_namespace", "default", "type",
				"ClusterIP", "port.http", "8080", "app", "service-wiremock"));

	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.discovery.reactive.enabled=false",
			"spring.cloud.discovery.blocking.enabled=true",
			"logging.level.org.springframework.cloud.kubernetes.commons.discovery=debug",
			"logging.level.org.springframework.cloud.client.discovery.health=debug",
			"logging.level.org.springframework.cloud.kubernetes.client.discovery=debug",
			"all.namespaces.all.labels=true" })
	class AllNamespacesAllLabels {

		/**
		 * <pre>
		 *     	Reactive is disabled, only blocking is active. As such,
		 * 	 	We assert for logs and call '/health' endpoint to see that blocking discovery
		 * 	 	client was initialized.
		 * </pre>
		 */
		@Test
		void test(CapturedOutput output) {
			assertBlockingConfiguration(output, port);
			assertAllNamespacesAllLabels(discoveryClient);
		}

	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.discovery.reactive.enabled=false",
			"spring.cloud.discovery.blocking.enabled=true",
			"logging.level.org.springframework.cloud.kubernetes.commons.discovery=debug",
			"logging.level.org.springframework.cloud.client.discovery.health=debug",
			"logging.level.org.springframework.cloud.kubernetes.client.discovery=debug",
			"all.namespaces.wiremock.labels=true" })
	class AllNamespacesWiremockLabels {

		// search in all namespaces, but only with 'app=service-wiremock' labels
		@Test
		void test() {
			assertAllNamespacesWiremockLabels(discoveryClient);
		}

	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.discovery.reactive.enabled=false",
			"spring.cloud.discovery.blocking.enabled=true",
			"logging.level.org.springframework.cloud.kubernetes.commons.discovery=debug",
			"logging.level.org.springframework.cloud.client.discovery.health=debug",
			"logging.level.org.springframework.cloud.kubernetes.client.discovery=debug",
			"default.namespace.all.labels=true" })
	class DefaultNamespaceAllLabels {

		// default namespace, all labels
		@Test
		void test() {
			assertDefaultNamespaceAllLabels(discoveryClient);
		}

	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		ApiClient client() {
			return apiClient();
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "all.namespaces.all.labels", havingValue = "true", matchIfMissing = false)
		KubernetesDiscoveryProperties kubernetesDiscoveryPropertiesAllNamespacesAllLabels() {
			return discoveryProperties(true, Set.of(), null, Map.of());
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "all.namespaces.wiremock.labels", havingValue = "true", matchIfMissing = false)
		KubernetesDiscoveryProperties kubernetesDiscoveryPropertiesAllNamespacesWiremockLabels() {
			return discoveryProperties(true, Set.of(), null, Map.of("app", "service-wiremock"));
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "default.namespace.all.labels", havingValue = "true", matchIfMissing = false)
		KubernetesDiscoveryProperties kubernetesDiscoveryPropertiesDefaultNamespaceAllLabels() {
			return discoveryProperties(false, Set.of(DEFAULT_NAMESPACE), null, Map.of());
		}

	}

}
