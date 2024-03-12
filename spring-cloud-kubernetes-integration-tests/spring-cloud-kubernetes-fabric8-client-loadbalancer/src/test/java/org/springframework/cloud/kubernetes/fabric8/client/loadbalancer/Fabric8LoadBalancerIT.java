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

package org.springframework.cloud.kubernetes.fabric8.client.loadbalancer;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;

/**
 * @author wind57
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Fabric8LoadBalancerIT {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(Fabric8LoadBalancerIT.class);

	private static final String WIREMOCK_URL = "http://localhost:80/loadbalancer/wiremock";

	private static final String HTTPD_URL = "http://localhost:80/loadbalancer/httpd";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-fabric8-client-loadbalancer";

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
		util.wiremock(NAMESPACE, "/wiremock", Phase.CREATE, false);
		util.httpd(NAMESPACE, Phase.CREATE);

		loadbalancerIt(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.wiremock(NAMESPACE, "/wiremock", Phase.DELETE, false);
		util.httpd(NAMESPACE, Phase.DELETE);
		loadbalancerIt(Phase.DELETE);
		Commons.cleanUp(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		Commons.systemPrune();
	}

	@Test
	@Order(1)
	void testLoadBalancerServiceMode() {
		testLoadBalancer();
	}

	private void testLoadBalancer() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(WIREMOCK_URL).build();

		String result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(result)).extractingJsonPathArrayValue("$.mappings").isEmpty();
		Assertions.assertThat(BASIC_JSON_TESTER.from(result)).extractingJsonPathNumberValue("$.meta.total")
				.isEqualTo(0);

		WebClient.Builder httpDBuilder = builder();
		WebClient httpdServiceClient = httpDBuilder.baseUrl(HTTPD_URL).build();
		String httpdResult = httpdServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block();
		Assertions.assertThat(httpdResult).contains("It works");

		String logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).contains("serviceID : service-wiremock");
		Assertions.assertThat(logs).contains("serviceID : service-httpd");
		Assertions.assertThat(logs).contains("discovering services in namespace : default");
	}

	private static void loadbalancerIt(Phase phase) {

		InputStream deploymentStream = util.inputStream(
			"spring-cloud-kubernetes-fabric8-client-loadbalancer-deployment.yaml");
		InputStream serviceStream = util.inputStream(
			"spring-cloud-kubernetes-fabric8-client-loadbalancer-service.yaml");
		InputStream ingressStream = util.inputStream(
			"spring-cloud-kubernetes-fabric8-client-loadbalancer-ingress.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);
		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);

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

}
