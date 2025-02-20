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

package org.springframework.cloud.kubernetes.discoveryclient.it;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
abstract class DiscoveryServerClientBase {

	private static final String DISCOVERY_SERVER_LABEL = "spring-cloud-kubernetes-discoveryserver";

	protected static final String NAMESPACE = "default";

	protected static final String NAMESPACE_LEFT = "left";

	protected static final String NAMESPACE_RIGHT = "right";

	protected static final String DISCOVERY_SERVER_APP_NAME = "spring-cloud-kubernetes-discoveryserver";

	protected static final K3sContainer K3S = Commons.container();

	protected static Util util;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		util = new Util(K3S);
	}

	protected static ApiClient apiClient() {
		String kubeConfigYaml = K3S.getKubeConfigYaml();

		ApiClient client;
		try {
			client = Config.fromConfig(new StringReader(kubeConfigYaml));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new CoreV1Api(client).getApiClient();
	}

	protected static KubernetesDiscoveryProperties discoveryProperties(Set<String> namespaces) {
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true, null, true,
				null, true, "port.", true, true);
		return new KubernetesDiscoveryProperties(true, false, namespaces, true, 60, false, null, Set.of(443, 8443),
				Map.of(), null, metadata, 0, false, true, "http://localhost:32321");
	}

	protected static void discoveryServer(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("manifests/discoveryserver-deployment.yaml");
		V1Service service = (V1Service) util.yaml("manifests/discoveryserver-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, null);
		}
	}

	protected static void serviceAccount(Phase phase) {

		try {
			V1ClusterRoleBinding clusterRoleBinding = (V1ClusterRoleBinding) util.yaml("manifests/cluster-role.yaml");
			RbacAuthorizationV1Api rbacApi = new RbacAuthorizationV1Api();

			if (phase == Phase.CREATE) {
				rbacApi.createClusterRoleBinding(clusterRoleBinding, null, null, null, null);
			}
			else {
				rbacApi.deleteClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), null, null, null, null,
						null, null);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

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
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> result.size() == 2);

		List<String> namespaces = result.stream().map(EndpointNameAndNamespace::namespace).toList();
		assertThat(namespaces).containsExactlyInAnyOrder(NAMESPACE_LEFT, NAMESPACE_RIGHT);

		List<String> endpointNames = result.stream().map(EndpointNameAndNamespace::endpointName).toList();
		assertThat(endpointNames.get(0)).contains("service-wiremock-deployment");
		assertThat(endpointNames.get(1)).contains("service-wiremock-deployment");
	}

}
