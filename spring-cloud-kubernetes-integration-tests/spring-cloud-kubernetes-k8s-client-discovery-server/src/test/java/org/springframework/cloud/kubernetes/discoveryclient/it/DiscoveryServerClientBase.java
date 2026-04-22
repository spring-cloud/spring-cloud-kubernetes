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

package org.springframework.cloud.kubernetes.discoveryclient.it;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
abstract class DiscoveryServerClientBase {

	private static final String DISCOVERY_SERVER_LABEL = "spring-cloud-kubernetes-discoveryserver";

	protected static final K3sContainer K3S = Commons.container();

	protected static NativeClientKubernetesFixture k8sNativeKubernetesFixture;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		k8sNativeKubernetesFixture = new NativeClientKubernetesFixture(K3S);
	}

	protected static KubernetesDiscoveryProperties discoveryProperties(Set<String> namespaces) {
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, null, true,
				null, true, "port.", true, true);
		return new KubernetesDiscoveryProperties(true, false, namespaces, true, 60, false, null, Set.of(443, 8443),
				Map.of(), null, metadata, 0, false, true, "http://localhost:32321");
	}

	protected static void testHeartBeat(HeartbeatListener heartbeatListener, CapturedOutput output) {

		// 1. logs from discovery server
		Commons.waitForLogStatement("using delay : 3000", K3S, DISCOVERY_SERVER_LABEL);
		Commons.waitForLogStatement("received heartbeat event", K3S, DISCOVERY_SERVER_LABEL);
		Commons.waitForLogStatement("state received :", K3S, DISCOVERY_SERVER_LABEL);

		// 2. logs from discovery client
		assertThat(output.getOut()).contains("using delay : 3000");
		assertThat(output.getOut()).contains("state received : ");
		assertThat(output.getOut()).contains("received heartbeat event in listener");

		// 3. heartbeat listener message
		List<EndpointNameAndNamespace> result = heartbeatListener.state.get();
		Awaitilities.awaitUntil(60, 1000, () -> result.size() == 2);

		List<String> namespaces = result.stream().map(EndpointNameAndNamespace::namespace).toList();
		assertThat(namespaces).containsExactlyInAnyOrder("left", "right");

		List<String> endpointNames = result.stream().map(EndpointNameAndNamespace::endpointName).toList();
		assertThat(endpointNames.get(0)).contains("service-wiremock-deployment");
		assertThat(endpointNames.get(1)).contains("service-wiremock-deployment");
	}

}
