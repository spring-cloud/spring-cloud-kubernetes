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

package org.springframework.cloud.kubernetes.client.reactive.discovery.it;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 */
class ReactiveDiscoveryClientIT {

	private static final String HEALTH_URL = "localhost:80/reactive-discovery-it/actuator/health";

	private static final String SERVICES_URL = "localhost:80/reactive-discovery-it/services";

	private static final String SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME = "spring-cloud-kubernetes-client-reactive-discoveryclient-it";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME, K3S);
		util = new Util(K3S);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME, K3S);
	}

	@BeforeEach
	void setup() {
		util.wiremock(NAMESPACE, "/wiremock", Phase.CREATE);
	}

	@AfterEach
	void after() {
		util.wiremock(NAMESPACE, "/wiremock", Phase.DELETE);
		reactiveDiscoveryIt(Phase.DELETE);
	}

	@Test
	void testReactiveDiscoveryClient() {
		reactiveDiscoveryIt(Phase.CREATE);
		testLoadBalancer();
		testHealth();
	}

	@SuppressWarnings("unchecked")
	private void testHealth() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(HEALTH_URL).build();
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> health = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> components = (Map<String, Object>) health.get("components");

		Assertions.assertTrue(components.containsKey("reactiveDiscoveryClients"));
		Map<String, Object> discoveryComposite = (Map<String, Object>) components.get("discoveryComposite");
		Assertions.assertEquals(discoveryComposite.get("status"), "UP");
	}

	private void testLoadBalancer() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(SERVICES_URL).build();
		String servicesResponse = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions
				.assertTrue(Arrays.stream(servicesResponse.split(",")).anyMatch("service-wiremock"::equalsIgnoreCase));
	}

	private void reactiveDiscoveryIt(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
				.yaml("spring-cloud-kubernetes-client-reactive-discoveryclient-it-deployment.yaml");
		V1Service service = (V1Service) util
				.yaml("spring-cloud-kubernetes-client-reactive-discoveryclient-it-service.yaml");
		V1Ingress ingress = (V1Ingress) util
				.yaml("spring-cloud-kubernetes-client-reactive-discoveryclient-it-ingress.yaml");

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

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
