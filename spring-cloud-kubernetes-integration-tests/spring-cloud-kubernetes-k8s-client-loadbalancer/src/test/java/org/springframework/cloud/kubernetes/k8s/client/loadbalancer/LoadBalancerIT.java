/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.loadbalancer;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithMerge;

/**
 * @author Ryan Baxter
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadBalancerIT {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(LoadBalancerIT.class);

	private static final String BODY_FOR_MERGE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-loadbalancer",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_LOADBALANCER_MODE",
									"value": "SERVICE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-k8s-client-loadbalancer");

	private static final String SERVICE_URL = "http://localhost:80/loadbalancer-it/service";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-k8s-client-loadbalancer";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		util = new Util(K3S);
		util.setUp(NAMESPACE);
		loadbalancerIt(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		loadbalancerIt(Phase.DELETE);
		Commons.cleanUp(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		Commons.systemPrune();
	}

	@BeforeEach
	void setup() {
		util.wiremock(NAMESPACE, "/wiremock", Phase.CREATE, false);
	}

	@AfterEach
	void afterEach() {
		util.wiremock(NAMESPACE, "/wiremock", Phase.DELETE, false);
	}

	@Test
	@Order(1)
	void testLoadBalancerPodMode() {
		testLoadBalancer();
	}

	@Test
	@Order(2)
	void testLoadBalancerServiceMode() {
		patchForServiceMode();
		testLoadBalancer();
	}

	private void testLoadBalancer() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(SERVICE_URL).build();

		String result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(result)).extractingJsonPathArrayValue("$.mappings").isEmpty();
		Assertions.assertThat(BASIC_JSON_TESTER.from(result)).extractingJsonPathNumberValue("$.meta.total")
				.isEqualTo(0);
	}

	private static void loadbalancerIt(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
				.yaml("spring-cloud-kubernetes-k8s-client-loadbalancer-deployment.yaml");
		V1Service service = (V1Service) util.yaml("spring-cloud-kubernetes-k8s-client-loadbalancer-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("spring-cloud-kubernetes-k8s-client-loadbalancer-ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static void patchForServiceMode() {
		patchWithMerge("spring-cloud-kubernetes-k8s-client-loadbalancer", LoadBalancerIT.NAMESPACE, BODY_FOR_MERGE,
				POD_LABELS);
	}

}
