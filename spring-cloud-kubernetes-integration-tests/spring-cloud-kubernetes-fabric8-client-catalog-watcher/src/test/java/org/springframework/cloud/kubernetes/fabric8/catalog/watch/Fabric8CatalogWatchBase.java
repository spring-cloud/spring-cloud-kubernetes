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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.test.context.TestPropertySource;

/**
 * @author wind57
 */

@TestPropertySource(
		properties = { "spring.main.cloud-platform=kubernetes", "spring.cloud.config.import-check.enabled=false",
				"spring.cloud.kubernetes.discovery.catalogServicesWatchDelay=2000",
				"spring.cloud.kubernetes.client.namespace=default",
				"logging.level.org.springframework.cloud.kubernetes.fabric8.discovery=DEBUG" })
@ExtendWith(OutputCaptureExtension.class)
abstract class Fabric8CatalogWatchBase {

	protected static final String NAMESPACE = "default";

	protected static final String NAMESPACE_A = "a";

	protected static final String NAMESPACE_B = "b";

	protected static final K3sContainer K3S = Commons.container();

	protected static Util util;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		util = new Util(K3S);
	}

	protected static KubernetesDiscoveryProperties discoveryProperties(boolean useEndpointSlices,
			Set<String> discoveryNamespaces) {
		return new KubernetesDiscoveryProperties(true, false, discoveryNamespaces, true, 60, false, null,
				Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, useEndpointSlices,
				false, null);
	}

	protected static KubernetesClient client() {
		String kubeConfigYaml = K3S.getKubeConfigYaml();
		Config config = Config.fromKubeconfig(kubeConfigYaml);
		return new KubernetesClientBuilder().withConfig(config).build();
	}

}
