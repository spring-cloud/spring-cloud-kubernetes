/*
 * Copyright 2013-2024 the original author or authors.
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

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.discoveryclient.it.DiscoveryClientFilterNamespaceDelegate.testNamespaceDiscoveryClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author Ryan Baxter
 */
class DiscoveryClientIT {

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-discovery-server",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "left"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_CATALOGSERVICESWATCHDELAY",
									"value": "3000"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_HTTP_DISCOVERY_CATALOG_WATCHER_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_TWO = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-discovery-server",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES",
									"value": "TRUE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_DISCOVERYSERVER",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_CATALOGSERVICESWATCHDELAY",
									"value": "3000"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_HTTP_DISCOVERY_CATALOG_WATCHER_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-k8s-client-discovery-server");

	private static final Map<String, String> POD_LABELS_DISCOVERY = Map.of("app",
			"spring-cloud-kubernetes-discoveryserver");

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(DiscoveryClientIT.class);

	private static final String DISCOVERY_SERVER_APP_NAME = "spring-cloud-kubernetes-discoveryserver";

	private static final String SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME = "spring-cloud-kubernetes-k8s-client-discovery-server";

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_LEFT = "left";

	private static final String NAMESPACE_RIGHT = "right";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static RbacAuthorizationV1Api rbacApi;

	private static V1ClusterRoleBinding clusterRoleBinding;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(DISCOVERY_SERVER_APP_NAME, K3S);

		Commons.validateImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);

		Images.loadWiremock(K3S);

		util = new Util(K3S);
		rbacApi = new RbacAuthorizationV1Api();
		util.setUp(NAMESPACE);

		util.createNamespace(NAMESPACE_LEFT);
		util.createNamespace(NAMESPACE_RIGHT);

		clusterRoleBinding = (V1ClusterRoleBinding) util
			.yaml("namespace-filter/cluster-admin-serviceaccount-role.yaml");
		rbacApi.createClusterRoleBinding(clusterRoleBinding, null, null, null, null);

		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_LEFT, Phase.CREATE, false);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock-" + NAMESPACE_RIGHT, Phase.CREATE, false);

		discoveryServer(Phase.CREATE);

	}

	@AfterAll
	static void afterAll() throws Exception {
		rbacApi.deleteClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), null, null, null, null, null,
				null);
		Commons.cleanUp(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.cleanUp(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);

		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_LEFT, Phase.DELETE, false);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock-" + NAMESPACE_RIGHT, Phase.DELETE, false);

		util.deleteNamespace(NAMESPACE_LEFT);
		util.deleteNamespace(NAMESPACE_RIGHT);

		discoveryServer(Phase.DELETE);
		discoveryClient(Phase.DELETE);
		Commons.systemPrune();
	}

	@Test
	void testDiscoveryClient() {
		discoveryClient(Phase.CREATE);
		testLoadBalancer();
		testHealth();

		patchForAllNamespaces("docker.io/springcloud/spring-cloud-kubernetes-discoveryserver:" + Commons.pomVersion(),
				"spring-cloud-kubernetes-discoveryserver-deployment", NAMESPACE);
		patchForNamespaceFilter(
				"docker.io/springcloud/spring-cloud-kubernetes-k8s-client-discovery-server:" + Commons.pomVersion(),
				"spring-cloud-kubernetes-k8s-client-discovery-server-deployment", NAMESPACE);
		testNamespaceDiscoveryClient(K3S);
	}

	private void testLoadBalancer() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/services").build();

		String result = serviceClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(result))
			.extractingJsonPathArrayValue("$")
			.contains("spring-cloud-kubernetes-discoveryserver");

		// since 'spring.cloud.kubernetes.http.discovery.client.catalog.watcher.enabled'
		// is false by default, we will not receive any heartbeat events,
		// simply because there are no beans registered to provide that to us.
		// We assert this by doing a call to our internal /state
		// endpoint, waiting 10 seconds and doing it again. Since the watch delay is set
		// to 3 seconds, if there would be proper events,
		// we would get a result that is different from '[]'.

		WebClient.Builder stateBuilder = builder();
		WebClient client = stateBuilder.baseUrl("http://localhost:80/discoveryclient-it/state").build();
		String stateResult = client.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(stateResult)).isEqualTo("[]");

		try {
			Thread.sleep(TimeUnit.SECONDS.toMillis(10));
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		String stateResultAfter10Seconds = client.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(stateResultAfter10Seconds)).isEqualTo("[]");
	}

	void testHealth() {
		WebClient.Builder clientBuilder = builder();
		WebClient.Builder serverBuilder = builder();

		WebClient client = clientBuilder.baseUrl("http://localhost:80/discoveryclient-it/actuator/health").build();
		WebClient server = serverBuilder.baseUrl("http://localhost:80/actuator/health").build();

		String clientHealth = client.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();
		String serverHealth = server.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(clientHealth))
			.extractingJsonPathStringValue("$.components.discoveryComposite.status")
			.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(serverHealth))
			.extractingJsonPathStringValue("$.components.kubernetes.status")
			.isEqualTo("UP");
	}

	private static void discoveryClient(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
			.yaml("client/spring-cloud-kubernetes-discoveryclient-it-deployment.yaml");
		V1Service service = (V1Service) util.yaml("client/spring-cloud-kubernetes-discoveryclient-it-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}
	}

	private static void discoveryServer(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
			.yaml("server/spring-cloud-kubernetes-discoveryserver-deployment.yaml");
		V1Service service = (V1Service) util.yaml("server/spring-cloud-kubernetes-discoveryserver-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, null);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	static void patchForNamespaceFilter(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchForAllNamespaces(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_TWO, POD_LABELS_DISCOVERY);
	}

}
