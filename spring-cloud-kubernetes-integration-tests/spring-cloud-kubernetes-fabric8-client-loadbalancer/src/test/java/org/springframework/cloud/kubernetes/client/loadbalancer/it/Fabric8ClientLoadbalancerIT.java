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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
public class Fabric8ClientLoadbalancerIT {

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-loadbalancer";

	private static KubernetesClient client;

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();
		util.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@BeforeEach
	void beforeEach() {
		util.wiremock(NAMESPACE, "/", Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.wiremock(NAMESPACE, "/", Phase.DELETE);
	}

	@Test
	void testLoadBalancerServiceMode() {

		manifests("SERVICE", Phase.CREATE);

		WebClient client = builder().baseUrl("localhost/loadbalancer-it/servicea").build();

		@SuppressWarnings("unchecked")
		Map<String, String> mapResult = (Map<String, String>) client.method(HttpMethod.GET).retrieve()
				.bodyToMono(Map.class).retryWhen(retrySpec()).block();

		Assertions.assertTrue(mapResult.containsKey("mappings"));
		Assertions.assertTrue(mapResult.containsKey("meta"));

		manifests("SERVICE", Phase.DELETE);

	}

	@Test
	public void testLoadBalancerPodMode() {

		manifests("POD", Phase.CREATE);

		WebClient client = builder().baseUrl("localhost/loadbalancer-it/servicea").build();

		@SuppressWarnings("unchecked")
		Map<String, String> mapResult = (Map<String, String>) client.method(HttpMethod.GET).retrieve()
				.bodyToMono(Map.class).retryWhen(retrySpec()).block();

		Assertions.assertTrue(mapResult.containsKey("mappings"));
		Assertions.assertTrue(mapResult.containsKey("meta"));

		manifests("SERVICE", Phase.DELETE);

	}

	private static void manifests(String type, Phase phase) {

		InputStream deploymentStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-deployment.yaml");
		InputStream serviceStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-service.yaml");
		InputStream ingressStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();
		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar activeProfileProperty = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_LOADBALANCER_MODE")
				.withValue(type).build();
		envVars.add(activeProfileProperty);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();

		if (phase.equals(Phase.CREATE)) {
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
