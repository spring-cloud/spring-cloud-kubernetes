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

package org.springframework.cloud.kubernetes.k8s.client.discovery.it;

import java.util.List;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.k8s.client.discovery.DiscoveryApp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.springframework.cloud.kubernetes.k8s.client.discovery.it.TestAssertions.assertLogStatement;

/**
 * @author wind57
 */
@SpringBootTest(classes = { DiscoveryApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesClientDiscoverySimpleIT extends KubernetesClientDiscoveryBase {

	@Autowired
	private DiscoveryClient discoveryClient;

	@BeforeEach
	void beforeEach() {
		Images.loadBusybox(K3S);
		util.busybox(NAMESPACE, Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.busybox(NAMESPACE, Phase.DELETE);
	}

	@Test
	void test(CapturedOutput output) throws Exception {

		// find both pods
		String[] both = K3S.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
			.getStdout()
			.split("\n");
		// add a label to first pod
		K3S.execInContainer("sh", "-c",
			"kubectl label pods " + both[0].split("/")[1] + " custom-label=custom-label-value");
		// add annotation to the second pod
		K3S.execInContainer("sh", "-c",
			"kubectl annotate pods " + both[1].split("/")[1] + " custom-annotation=custom-annotation-value");

		assertLogStatement(output, "serviceSharedInformer will use namespace : default");

		List<String> services = discoveryClient.getServices();
		System.out.println("111111 : " + services);

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
			return discoveryProperties(false, Set.of(NAMESPACE));
		}

	}

}
