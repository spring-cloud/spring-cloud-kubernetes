/*
 * Copyright 2013-2021 the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author mbialkowski1
 */
class DiscoveryClientFilterNamespaceIT {

	private static final String DISCOVERY_SERVER_APP_NAME = "spring-cloud-kubernetes-discoveryserver";

	private static final String SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME = "spring-cloud-kubernetes-discoveryclient-it";

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_LEFT = "left";

	private static final String NAMESPACE_RIGHT = "right";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(DISCOVERY_SERVER_APP_NAME, K3S);

		Commons.validateImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);

		util = new Util(K3S);
		util.createNamespace(NAMESPACE_LEFT);
		util.createNamespace(NAMESPACE_RIGHT);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_LEFT, NAMESPACE_RIGHT));

		discoveryServer(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.cleanUp(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);
		discoveryServer(Phase.DELETE);
		util.deleteNamespace(NAMESPACE_LEFT);
		util.deleteNamespace(NAMESPACE_RIGHT);
	}

	@AfterEach
	void afterEach() {
		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_LEFT, Phase.DELETE);
		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_RIGHT, Phase.DELETE);
		discoveryIt(Phase.DELETE);
	}

	@Test
	void testDiscoveryClient() {
		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_LEFT, Phase.CREATE);
		util.wiremock(NAMESPACE_LEFT, "/wiremock-" + NAMESPACE_RIGHT, Phase.CREATE);
		discoveryIt(Phase.CREATE);

		testLoadBalancer();
		testHealth();
	}

	private void testLoadBalancer() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/services").build();

		String[] result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String[].class)
				.retryWhen(retrySpec()).block();
		assertThat(result).containsAnyOf("wiremock");

		// ServiceInstance
		WebClient serviceInstanceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/service/wiremock")
				.build();
		List<KubernetesServiceInstance> serviceInstances = serviceInstanceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<KubernetesServiceInstance>>() {
				}).retryWhen(retrySpec()).block();

		assertThat(serviceInstances).isNotNull();
		assertThat(serviceInstances.size()).isEqualTo(1);
		assertThat(serviceInstances.get(0).getNamespace()).isEqualTo(NAMESPACE_LEFT);

	}

	@SuppressWarnings("unchecked")
	void testHealth() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/actuator/health").build();

		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> health = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> components = (Map<String, Object>) health.get("components");

		Map<String, Object> discoveryComposite = (Map<String, Object>) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");
	}

	private void discoveryIt(Phase phase) {

		V1Deployment deployment = (V1Deployment) util
				.yaml("client/spring-cloud-kubernetes-discoveryclient-it-deployment.yaml");
		V1Service service = (V1Service) util.yaml("client/spring-cloud-kubernetes-discoveryclient-it-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("client/spring-cloud-kubernetes-discoveryclient-it-ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			// add namespaces filter property for left namespace
			var env = new V1EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0")
					.withValue(NAMESPACE_LEFT).build();
			var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
			container.setEnv(List.of(env));

			util.createAndWait(NAMESPACE_LEFT, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE_LEFT, deployment, service, ingress);
		}

	}

	private static void discoveryServer(Phase phase) {

		V1Deployment deployment = (V1Deployment) util
				.yaml("server/spring-cloud-kubernetes-discoveryserver-deployment.yaml");
		V1Service service = (V1Service) util.yaml("server/spring-cloud-kubernetes-discoveryserver-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("server/spring-cloud-kubernetes-discoveryserver-ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			// add namespaces filter property for left namespace
			// setup all-namespaces property
			V1EnvVar env = new V1EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES")
					.withValue("TRUE").build();
			V1Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
			container.setEnv(List.of(env));

			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
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
