/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
		"spring.cloud.config.import-check.enabled=false", "spring.cloud.kubernetes.client.namespace=default",
		"spring.cloud.kubernetes.discovery.metadata.add-pod-labels=true",
		"spring.cloud.kubernetes.discovery.metadata.add-pod-annotations=true",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.discovery=debug" })
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = { Fabric8DiscoveryApp.class, Fabric8DiscoveryBase.TestConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class Fabric8DiscoveryBase {

	protected static final String NAMESPACE = "default";

	protected static final K3sContainer K3S = Commons.container();

	protected static Util util;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		util = new Util(K3S);
	}

	protected static KubernetesDiscoveryProperties discoveryProperties(boolean allNamespaces, Set<String> namespaces,
			String filter, Map<String, String> labels) {
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, null, true,
				null, true, "port.", true, true);
		return new KubernetesDiscoveryProperties(true, allNamespaces, namespaces, true, 60, false, filter,
				Set.of(443, 8443), labels, null, metadata, 0, false, true, null);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		KubernetesClient kubernetesClient() {
			String kubeConfigYaml = K3S.getKubeConfigYaml();
			Config config = Config.fromKubeconfig(kubeConfigYaml);
			return new KubernetesClientBuilder().withConfig(config).build();
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "all.namespaces.no.labels", havingValue = "true", matchIfMissing = false)
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
			return discoveryProperties(true, Set.of(), null, Map.of());
		}

		@Bean
		@Primary
		@ConditionalOnProperty(value = "all.namespaces.wiremock.labels", havingValue = "true", matchIfMissing = false)
		KubernetesDiscoveryProperties kubernetesDiscoveryPropertiesAllNamespacesWiremockLabels() {
			return discoveryProperties(true, Set.of(), null, Map.of("app", "service-wiremock"));
		}

	}

}
