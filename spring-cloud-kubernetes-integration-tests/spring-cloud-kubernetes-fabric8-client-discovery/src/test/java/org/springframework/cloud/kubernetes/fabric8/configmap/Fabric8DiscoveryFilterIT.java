/*
 * Copyright 2013-2023 the original author or authors.
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
import java.util.Comparator;
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

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

class Fabric8DiscoveryFilterIT {

	private static final String FILTER_BOTH_NAMESPACES = "#root.metadata.namespace matches '^.*uat$'";

	private static final String FILTER_SINGLE_NAMESPACE = "#root.metadata.namespace matches 'a-uat$'";

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

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

	@BeforeEach
	void beforeEach() {
		util.createNamespace(NAMESPACE_A_UAT);
		util.createNamespace(NAMESPACE_B_UAT);
		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.CREATE);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.DELETE);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.DELETE);
		util.deleteNamespace(NAMESPACE_A_UAT);
		util.deleteNamespace(NAMESPACE_B_UAT);
	}

	@AfterAll
	static void after() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches '^uat.*$'"
	 *
	 *     As such, both services are found via 'getInstances' call.
	 * </pre>
	 */
	@Test
	void filterMatchesBothNamespacesViaThePredicate() {

		manifests(Phase.CREATE, FILTER_BOTH_NAMESPACES);

		WebClient clientServices = builder().baseUrl("http://localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient client = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(serviceInstances.size(), 2);
		List<DefaultKubernetesServiceInstance> sorted = serviceInstances.stream()
				.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::getNamespace)).toList();

		DefaultKubernetesServiceInstance first = sorted.get(0);
		Assertions.assertEquals(first.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(first.getInstanceId());
		Assertions.assertEquals(first.getPort(), 8080);
		Assertions.assertEquals(first.getNamespace(), "a-uat");
		Assertions.assertEquals(first.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

		DefaultKubernetesServiceInstance second = sorted.get(1);
		Assertions.assertEquals(second.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(second.getInstanceId());
		Assertions.assertEquals(second.getPort(), 8080);
		Assertions.assertEquals(second.getNamespace(), "b-uat");
		Assertions.assertEquals(second.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "b-uat", "type", "ClusterIP"));

		manifests(Phase.DELETE, FILTER_BOTH_NAMESPACES);
	}

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches 'a-uat$'"
	 *
	 *     As such, only service from 'a-uat' namespace matches.
	 * </pre>
	 */
	@Test
	void filterMatchesOneNamespaceViaThePredicate() {
		manifests(Phase.CREATE, FILTER_SINGLE_NAMESPACE);

		WebClient clientServices = builder().baseUrl("http://localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient client = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(serviceInstances.size(), 1);

		DefaultKubernetesServiceInstance first = serviceInstances.get(0);
		Assertions.assertEquals(first.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(first.getInstanceId());
		Assertions.assertEquals(first.getPort(), 8080);
		Assertions.assertEquals(first.getNamespace(), "a-uat");
		Assertions.assertEquals(first.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

		manifests(Phase.DELETE, FILTER_SINGLE_NAMESPACE);
	}

	private static void manifests(Phase phase, String serviceFilter) {

		InputStream deploymentStream = util.inputStream("fabric8-discovery-deployment.yaml");
		InputStream serviceStream = util.inputStream("fabric8-discovery-service.yaml");
		InputStream ingressStream = util.inputStream("fabric8-discovery-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();
		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar namespaceAUat = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0")
				.withValue(NAMESPACE_A_UAT).build();
		EnvVar namespaceBUat = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1")
				.withValue(NAMESPACE_B_UAT).build();
		EnvVar filter = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_FILTER")
				.withValue(serviceFilter).build();
		EnvVar debug = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY").withValue("DEBUG")
				.build();
		envVars.add(namespaceAUat);
		envVars.add(namespaceBUat);
		envVars.add(filter);
		envVars.add(debug);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();

		if (phase.equals(Phase.CREATE)) {
			client.rbac().clusterRoleBindings().resource(client.rbac().clusterRoleBindings().load(getAdminRole()).get())
					.create();
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			client.rbac().clusterRoleBindings().resource(client.rbac().clusterRoleBindings().load(getAdminRole()).get())
					.delete();
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
