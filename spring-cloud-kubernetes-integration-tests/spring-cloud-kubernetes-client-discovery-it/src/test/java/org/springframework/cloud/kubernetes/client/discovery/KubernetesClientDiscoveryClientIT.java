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

package org.springframework.cloud.kubernetes.client.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryClientIT {

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_A = "a";

	private static final String NAMESPACE_B = "b";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-discovery-it";

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
	}

	@AfterAll
	static void after() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	/**
	 * Three services are deployed in the default namespace. We do not configure any
	 * explicit namespace and 'default' must be picked-up.
	 */
	@Test
	void testSimple() {

		// set-up
		util.setUp(NAMESPACE);
		manifests(false, null, Phase.CREATE);
		util.busybox(NAMESPACE, Phase.CREATE);

		assertLogStatement("serviceSharedInformer will use namespace : default");

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 3);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-client-discovery-it"));
		Assertions.assertTrue(servicesResult.contains("busybox-service"));

		WebClient ourServiceClient = builder()
				.baseUrl("http://localhost//service-instances/spring-cloud-kubernetes-client-discovery-it").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		Assertions.assertNotNull(serviceInstance.getInstanceId());
		Assertions.assertEquals(serviceInstance.getServiceId(), "spring-cloud-kubernetes-client-discovery-it");
		Assertions.assertNotNull(serviceInstance.getHost());
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it", "custom-spring-k8s", "spring-k8s",
					"http", "8080", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertEquals(serviceInstance.getNamespace(), "default");

		WebClient busyBoxServiceClient = builder().baseUrl("http://localhost//service-instances/busybox-service")
				.build();
		List<DefaultKubernetesServiceInstance> busyBoxServiceInstances = busyBoxServiceClient.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(busyBoxServiceInstances.size(), 2);

		// clean-up
		util.busybox(NAMESPACE, Phase.DELETE);
		manifests(false, null, Phase.DELETE);
	}

	/**
	 * <pre>
	 *     - config server is enabled for all namespaces
	 *     - wiremock service is deployed in namespace-a
	 *     - busybox service is deployed in namespace-b
	 *
	 *     Our discovery searches in all namespaces, thus finds them both.
	 * </pre>
	 */
	@Test
	void testAllNamespaces() {
		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWideClusterRoleBinding(NAMESPACE);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);
		manifests(true, null, Phase.CREATE);

		assertLogStatement("serviceSharedInformer will use all-namespaces");

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();
		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();
		Assertions.assertEquals(servicesResult.size(), 7);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-client-discovery-it"));
		Assertions.assertTrue(servicesResult.contains("busybox-service"));
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		manifests(true, null, Phase.DELETE);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.DELETE);
		util.busybox(NAMESPACE_B, Phase.DELETE);
		util.deleteClusterWideClusterRoleBinding(NAMESPACE);
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	/**
	 * <pre>
	 *     - config server is enabled for namespace-a
	 *     - wiremock service is deployed in namespace-a
	 *     - wiremock service is deployed in namespace-b
	 *
	 *     Only service in namespace-a is found.
	 * </pre>
	 */
	@Test
	void testSpecificNamespace() {
		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE_A));
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.CREATE);
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.CREATE);
		manifests(false, NAMESPACE_A, Phase.CREATE);

		// first check that wiremock service is present in both namespaces a and b
		assertServicePresentInNamespaces(List.of("a", "b"), "service-wiremock", "service-wiremock");
		assertLogStatement("serviceSharedInformer will use namespace : a");

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();
		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();
		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient wiremockInNamespaceAClient = builder().baseUrl("http://localhost//service-instances/service-wiremock")
				.build();

		List<DefaultKubernetesServiceInstance> wiremockInNamespaceA = wiremockInNamespaceAClient.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(wiremockInNamespaceA.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = wiremockInNamespaceA.get(0);
		Assertions.assertEquals(serviceInstance.getNamespace(), "a");

		manifests(false, NAMESPACE_A, Phase.DELETE);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.DELETE);
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.DELETE);
		util.deleteClusterWide(NAMESPACE, Set.of(NAMESPACE_A));
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	private static void manifests(boolean allNamespaces, String clientSpecificNamespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("kubernetes-discovery-deployment.yaml");
		V1Service service = (V1Service) util.yaml("kubernetes-discovery-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("kubernetes-discovery-ingress.yaml");

		List<V1EnvVar> envVars = new ArrayList<>(
				Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
						.orElse(List.of()));
		V1EnvVar debugLevel = new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY")
				.value("DEBUG");
		if (allNamespaces) {
			V1EnvVar allNamespacesVar = new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES")
					.value("TRUE");
			envVars.add(allNamespacesVar);
		}

		if (clientSpecificNamespace != null) {
			V1EnvVar clientNamespace = new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE")
					.value(NAMESPACE_A);
			envVars.add(clientNamespace);
		}
		envVars.add(debugLevel);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

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

	private void assertLogStatement(String message) {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			String ok = execResult.getStdout();
			Assertions.assertTrue(ok.contains(message));
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	private void assertServicePresentInNamespaces(List<String> namespaces, String value, String serviceName) {
		namespaces.forEach(x -> {
			try {
				String service = K3S.execInContainer("sh", "-c",
						"kubectl get services -n " + x + " -l app=" + value + " -o=name --no-headers | tr -d '\n'")
						.getStdout();
				Assertions.assertEquals(service, "service/" + serviceName);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}

		});
	}

}
