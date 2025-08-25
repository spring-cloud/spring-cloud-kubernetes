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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

/**
 * @author wind57
 */
@SpringBootTest(classes = { DiscoveryApp.class, KubernetesClientDiscoveryFilterIT.TestConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.namespaces[0]=a-uat",
		"spring.cloud.kubernetes.discovery.namespaces[1]=b-uat" })
class KubernetesClientDiscoveryFilterIT extends KubernetesClientDiscoveryBase {

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	@Autowired
	private DiscoveryClient discoveryClient;

	@BeforeEach
	void beforeEach() {
		util.createNamespace(NAMESPACE_A_UAT);
		util.createNamespace(NAMESPACE_B_UAT);

		Images.loadWiremock(K3S);
		util.wiremock(NAMESPACE_A_UAT, Phase.CREATE, false);
		util.wiremock(NAMESPACE_B_UAT, Phase.CREATE, false);
	}

	@AfterEach
	void afterEach() {
		util.wiremock(NAMESPACE_A_UAT, Phase.DELETE, false);
		util.wiremock(NAMESPACE_B_UAT, Phase.DELETE, false);

		util.deleteNamespace(NAMESPACE_A_UAT);
		util.deleteNamespace(NAMESPACE_B_UAT);
	}

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches '^uat.*$'"
	 *
	 *     As such, both services are found via 'getInstances' call.
	 * </pre>
	 */
	@Test
	void test() {
		List<String> services = discoveryClient.getServices();
		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock");

		assertThat(services.size()).isEqualTo(1);
		assertThat(services).contains("service-wiremock");
		assertThat(serviceInstances.size()).isEqualTo(2);

		List<DefaultKubernetesServiceInstance> sorted = serviceInstances.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::getNamespace))
			.toList();

		DefaultKubernetesServiceInstance first = sorted.get(0);
		assertThat(first.getServiceId()).isEqualTo("service-wiremock");
		assertThat(first.getInstanceId()).isNotNull();
		assertThat(first.getPort()).isEqualTo(8080);
		assertThat(first.getNamespace()).isEqualTo("a-uat");
		assertThat(first.getMetadata()).containsAllEntriesOf(
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

		DefaultKubernetesServiceInstance second = sorted.get(1);
		assertThat(second.getServiceId()).isEqualTo("service-wiremock");
		assertThat(second.getInstanceId()).isNotNull();
		assertThat(second.getPort()).isEqualTo(8080);
		assertThat(second.getNamespace()).isEqualTo("b-uat");
		assertThat(second.getMetadata()).containsAllEntriesOf(
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "b-uat", "type", "ClusterIP"));
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
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
			return discoveryProperties(false, Set.of(NAMESPACE_A_UAT, NAMESPACE_B_UAT),
					"#root.metadata.namespace matches '^.*uat$'");
		}

	}

}
