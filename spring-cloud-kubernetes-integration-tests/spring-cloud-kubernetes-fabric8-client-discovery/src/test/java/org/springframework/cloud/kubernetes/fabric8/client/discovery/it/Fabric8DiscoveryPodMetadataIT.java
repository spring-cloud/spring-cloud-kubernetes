/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.discovery.it;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryApp;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.it.TestAssertions.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.it.TestAssertions.retrySpec;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.it.Fabric8DiscoveryPodMetadataIT.TestConfig;

/**
 * @author wind57
 */
@SpringBootTest(classes = { Fabric8DiscoveryApp.class, TestConfig.class },
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Fabric8DiscoveryPodMetadataIT extends Fabric8DiscoveryBase {

	@LocalServerPort
	private int port;

	@BeforeEach
	void beforeEach() {
		Images.loadBusybox(K3S);
		util.busybox(NAMESPACE, Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.busybox(NAMESPACE, Phase.DELETE);
	}

	/**
	 * <pre>
	 * 		- there is a 'busybox-service' service deployed with two pods
	 * 		- find each of the pod, add annotation to one and labels to another
	 * 		- call DiscoveryClient::getInstances with this serviceId and assert fields returned
	 * </pre>
	 */
	@Test
	void test() throws Exception {
		String[] busyboxPods = K3S.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
			.getStdout()
			.split("\n");

		String podOne = busyboxPods[0].split("/")[1];
		String podTwo = busyboxPods[1].split("/")[1];

		K3S.execInContainer("sh", "-c", "kubectl label pods " + podOne + " my-label=my-value");
		K3S.execInContainer("sh", "-c", "kubectl annotate pods " + podTwo + " my-annotation=my-value");
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		KubernetesClient kubernetesClient() {
			return client();
		}

	}

}
