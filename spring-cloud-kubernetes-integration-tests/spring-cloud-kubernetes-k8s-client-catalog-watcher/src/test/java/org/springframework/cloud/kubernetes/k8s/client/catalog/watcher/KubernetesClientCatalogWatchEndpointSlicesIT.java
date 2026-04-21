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

package org.springframework.cloud.kubernetes.k8s.client.catalog.watcher;

import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.TestAssertions.assertLogStatement;
import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.TestAssertions.invokeAndAssert;

@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@NativeClientIntegrationTest(namespaces = { "a", "b" }, busyboxNamespaces = { "a", "b" })
class KubernetesClientCatalogWatchEndpointSlicesIT extends KubernetesClientCatalogWatchBase {

	@LocalServerPort
	private int port;

	@TestBean
	private ApiClient apiClient;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

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
	void testCatalogWatchWithEndpoints(CapturedOutput output, NativeClientKubernetesFixture fixture) {
		assertLogStatement(output, "stateGenerator is of type: KubernetesClientEndpointSlicesCatalogWatch");
		invokeAndAssert(fixture, Set.of("a", "b"), port, "a");
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(true, Set.of("default", "a"));
	}

}
