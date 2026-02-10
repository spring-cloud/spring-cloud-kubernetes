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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.assertBlockingConfiguration;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.assertPodMetadata;

class Fabric8DiscoveryBlockingIT extends Fabric8DiscoveryBase {

	@LocalManagementPort
	private int port;

	@Autowired
	private DiscoveryClient discoveryClient;

	@BeforeAll
	static void beforeEach() {
		Images.loadBusybox(K3S);
		Images.loadWiremock(K3S);

		util.busybox(NAMESPACE, Phase.CREATE);
		util.wiremock(NAMESPACE, Phase.CREATE, false);
	}

	@AfterAll
	static void afterEach() {
		util.busybox(NAMESPACE, Phase.DELETE);
		util.wiremock(NAMESPACE, Phase.DELETE, false);
	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.discovery.reactive.enabled=false",
			"logging.level.org.springframework.cloud.client.discovery.health=DEBUG",
			"logging.level.org.springframework.cloud.kubernetes.commons.discovery=DEBUG",
			"all.namespaces.no.labels=true" })
	class AllNamespacesNoLabels {

		@Test
		void test(CapturedOutput output) throws Exception {

			String[] busyboxPods = K3S
				.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
				.getStdout()
				.split("\n");

			String podOne = busyboxPods[0].split("/")[1];
			String podTwo = busyboxPods[1].split("/")[1];

			K3S.execInContainer("sh", "-c", "kubectl label pods " + podOne + " my-label=my-value");
			K3S.execInContainer("sh", "-c", "kubectl annotate pods " + podTwo + " my-annotation=my-value");

			assertBlockingConfiguration(output, port);
			assertPodMetadata(discoveryClient);

			Assertions.assertThat(discoveryClient.getServices())
				.contains("kubernetes", "busybox-service", "service-wiremock");
		}

	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.discovery.reactive.enabled=false",
			"logging.level.org.springframework.cloud.client.discovery.health=DEBUG",
			"logging.level.org.springframework.cloud.kubernetes.commons.discovery=DEBUG",
			"all.namespaces.wiremock.labels=true" })
	class AllNamespacesWiremockLabels {

		@Test
		void test() {
			Assertions.assertThat(discoveryClient.getServices())
				.contains("service-wiremock")
				.doesNotContain("busybox-service");
		}

	}

}
