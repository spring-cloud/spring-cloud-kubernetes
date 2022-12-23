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

package org.springframework.cloud.kubernetes.fabric8.configmap;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author mbialkowski1
 */
class Fabric8DiscoveryNamespaceFilterIT {

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_LEFT = "namespace-left";

	private static final String NAMESPACE_RIGHT = "namespace-right";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static Util util;

	private static KubernetesClient client;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();
		util.setUp(NAMESPACE);

		manifests(Phase.CREATE);
		util.createNamespace(NAMESPACE_LEFT);
		util.createNamespace(NAMESPACE_RIGHT);
		util.wiremock(NAMESPACE_LEFT, "/wiremock", Phase.CREATE);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock", Phase.CREATE);
	}

	@AfterAll
	static void after() throws Exception {
		manifests(Phase.DELETE);
		util.wiremock(NAMESPACE_LEFT, "/wiremock", Phase.DELETE);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock", Phase.DELETE);
		util.deleteNamespace(NAMESPACE_LEFT);
		util.deleteNamespace(NAMESPACE_RIGHT);
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@Test
	void test() {
		WebClient clientServices = builder().baseUrl("localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient clientEndpoints = builder().baseUrl("localhost/endpoints/wiremock").build();

		List<Endpoints> endpoints = clientEndpoints.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<Endpoints>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(endpoints.size(), 1);
		Assertions.assertEquals(endpoints.get(0).getMetadata().getNamespace(), NAMESPACE_LEFT);

	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("fabric8-discovery-deployment.yaml");
		InputStream serviceStream = util.inputStream("fabric8-discovery-service.yaml");
		InputStream ingressStream = util.inputStream("fabric8-discovery-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();
		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar activeProfileProperty = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0")
				.withValue(NAMESPACE_LEFT).build();
		envVars.add(activeProfileProperty);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();

		if (phase.equals(Phase.CREATE)) {
			client.rbac().clusterRoleBindings().resource(client.rbac().clusterRoleBindings().load(getAdminRole()).get())
					.create();
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private static InputStream getAdminRole() {
		return util.inputStream("namespace-filter/fabric8-cluster-admin-serviceaccount-role.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
