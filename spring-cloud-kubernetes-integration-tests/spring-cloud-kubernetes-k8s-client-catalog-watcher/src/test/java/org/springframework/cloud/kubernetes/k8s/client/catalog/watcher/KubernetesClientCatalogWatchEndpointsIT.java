/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.catalog.watcher;

import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.TestAssertions.assertLogStatement;
import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.TestAssertions.invokeAndAssert;

@SpringBootTest(classes = { KubernetesClientCatalogWatchEndpointsIT.TestConfig.class, Application.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesClientCatalogWatchEndpointsIT extends KubernetesClientCatalogWatchBase {

	@LocalServerPort
	private int port;

	@BeforeEach
	void beforeEach() {

		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);

		Images.loadBusybox(K3S);

		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);

	}

	@AfterEach
	void afterEach() {
		// busybox is deleted as part of the assertions, thus not seen here
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods in two namespaces : a, b
	 *     - we use endpoints
	 *     - we enable namespace filtering for 'default' and 'a'
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service in 'a' and 'b'
	 *     - assert that we receive an empty response
	 * </pre>
	 */
	@Test
	void testCatalogWatchWithEndpoints(CapturedOutput output) {
		assertLogStatement(output, "stateGenerator is of type: KubernetesEndpointsCatalogWatch");
		invokeAndAssert(util, Set.of(NAMESPACE_A, NAMESPACE_B), port, NAMESPACE_A);
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
			return discoveryProperties(false, Set.of(NAMESPACE, NAMESPACE_A));
		}

	}

}
