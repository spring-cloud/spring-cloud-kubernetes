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

import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.springframework.cloud.kubernetes.k8s.client.discovery.TestAssertions.assertPodMetadata;
import static org.springframework.cloud.kubernetes.k8s.client.discovery.TestAssertions.assertReactiveConfiguration;

/**
 * @author wind57
 */
@SpringBootTest(classes = { DiscoveryApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
		properties = { "spring.cloud.discovery.reactive.enabled=true", "spring.cloud.discovery.blocking.enabled=false",
				"logging.level.org.springframework.cloud.kubernetes.commons.discovery=debug",
				"logging.level.org.springframework.cloud.client.discovery.health=debug",
				"logging.level.org.springframework.cloud.kubernetes.client.discovery=debug" })
@NativeClientIntegrationTest(wiremock = @NativeClientIntegrationTest.Wiremock(enabled = true,
		namespaces = { "default" }, withNodePort = true))
class KubernetesClientReactiveIT extends KubernetesClientDiscoveryBase {

	@TestBean
	private ApiClient apiClient;

	@TestBean
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	@LocalManagementPort
	private int port;

	@Autowired
	private ReactiveDiscoveryClient discoveryClient;

	/**
	 * <pre>
	 *
	 *     	Reactive is enabled, only blocking is disabled. As such,
	 * 	 	We assert for logs and call '/health' endpoint to see that blocking discovery
	 * 	 	client was initialized.
	 *
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		assertReactiveConfiguration(output, port);
		assertPodMetadata(discoveryClient);
	}

	private static KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
		return discoveryProperties(false, Set.of(DEFAULT_NAMESPACE), null, Map.of());
	}

}
