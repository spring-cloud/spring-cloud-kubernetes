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

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Fabric8ClientKubernetesFixture;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.Fabric8ClientIntegrationTest;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.springframework.cloud.kubernetes.fabric8.discovery.TestAssertions.assertLogStatement;
import static org.springframework.cloud.kubernetes.fabric8.discovery.TestAssertions.invokeAndAssert;

/**
 * @author wind57
 */
@SpringBootTest(classes = { Fabric8CatalogWatchAutoConfiguration.class, Application.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Fabric8ClientIntegrationTest(namespaces = { "a", "b" }, busyboxNamespaces = { "a", "b" })
class Fabric8CatalogWatchEndpointSlicesIT extends Fabric8CatalogWatchBase {

	@TestBean
	private KubernetesClient client;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	@LocalServerPort
	private int port;

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods in two namespaces : a, b
	 *     - we use endpoint slices
	 *     - we enable namespace filtering for 'default' and 'a'
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service in 'a' and 'b'
	 *     - assert that we receive an empty response
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output, Fabric8ClientKubernetesFixture fixture) {
		assertLogStatement(output, "stateGenerator is of type: Fabric8EndpointSliceCatalogWatch");
		invokeAndAssert(fixture, Set.of("a", "b"), port, "a");
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(true, Set.of("default", "a"));
	}

	private static KubernetesClient client() {
		// K3sContextInitializer makes sure it is started
		String kubeConfigYaml = Commons.container().getKubeConfigYaml();
		Config config = Config.fromKubeconfig(kubeConfigYaml);
		return new KubernetesClientBuilder().withConfig(config).build();
	}

}
