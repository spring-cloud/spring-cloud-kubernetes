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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Set;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.springframework.cloud.kubernetes.fabric8.discovery.TestAssertions.assertLogStatement;
import static org.springframework.cloud.kubernetes.fabric8.discovery.TestAssertions.invokeAndAssert;

/**
 * @author wind57
 */
@SpringBootTest(classes = { Fabric8CatalogWatchAutoConfiguration.class, Application.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Fabric8CatalogWatchEndpointsIT extends Fabric8CatalogWatchBase {

	@TestBean
	private KubernetesClient client;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

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
	void test(CapturedOutput output) {
		assertLogStatement(output, "stateGenerator is of type: Fabric8EndpointsCatalogWatch");
		invokeAndAssert(util, Set.of(NAMESPACE_A, NAMESPACE_B), port, NAMESPACE_A);
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(false, Set.of(NAMESPACE, NAMESPACE_A));
	}

}
