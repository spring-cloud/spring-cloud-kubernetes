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

package org.springframework.cloud.kubernetes.client.discovery.it;

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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.client.ServiceInstance;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KubernetesClientDiscoveryClientIT {

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_A = "a";

	private static final String NAMESPACE_B = "b";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-discovery-it";

	private static final String DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-discovery-deployment-it";

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		util.setUp(NAMESPACE);
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	/**
	 * Three services are deployed in the default namespace. We do not configure any
	 * explicit namespace and 'default' must be picked-up.
	 */
	@Test
	@Order(1)
	void testSimple() {

		util.busybox(NAMESPACE, Phase.CREATE);

		Assertions.assertTrue(logs().contains("serviceSharedInformer will use namespace : default"));

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 3);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-client-discovery-it"));
		Assertions.assertTrue(servicesResult.contains("busybox-service"));

		WebClient ourServiceClient = builder()
				.baseUrl("http://localhost/service-instances/spring-cloud-kubernetes-client-discovery-it").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		Assertions.assertNotNull(serviceInstance.getInstanceId());
		Assertions.assertEquals(serviceInstance.getServiceId(), "spring-cloud-kubernetes-client-discovery-it");
		Assertions.assertNotNull(serviceInstance.getHost());
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it", "custom-spring-k8s", "spring-k8s", "http",
						"8080", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertEquals(serviceInstance.getNamespace(), "default");

		WebClient busyBoxServiceClient = builder().baseUrl("http://localhost/service-instances/busybox-service")
				.build();
		List<DefaultKubernetesServiceInstance> busyBoxServiceInstances = busyBoxServiceClient.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(busyBoxServiceInstances.size(), 2);

		// enforces this :
		// https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1286
		WebClient clientForNonExistentService = builder().baseUrl("http://localhost/service-instances/non-existent")
				.build();
		List<ServiceInstance> resultForNonExistentService = clientForNonExistentService.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<ServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(resultForNonExistentService.size(), 0);

		// clean-up
		util.busybox(NAMESPACE, Phase.DELETE);
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
	@Order(2)
	void testAllNamespaces() {
		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWideClusterRoleBinding(NAMESPACE);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);

		KubernetesClientDiscoveryClientUtils.patchForAllNamespaces(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		Assertions.assertTrue(logs().contains("serviceSharedInformer will use all-namespaces"));

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();
		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();
		Assertions.assertEquals(servicesResult.size(), 7);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-client-discovery-it"));
		Assertions.assertTrue(servicesResult.contains("busybox-service"));
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		// enforces this :
		// https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1286
		WebClient clientForNonExistentService = builder().baseUrl("http://localhost/service-instances/non-existent")
				.build();
		List<ServiceInstance> resultForNonExistentService = clientForNonExistentService.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<ServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(resultForNonExistentService.size(), 0);

		// do not remove wiremock in namespace a, it is required in the next test
		util.busybox(NAMESPACE_B, Phase.DELETE);
		util.deleteClusterWideClusterRoleBinding(NAMESPACE);
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
	@Order(3)
	void testSpecificNamespace() {
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A));
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.CREATE);

		KubernetesClientDiscoveryClientUtils.patchForSingleNamespace(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		// first check that wiremock service is present in both namespaces a and b
		assertServicePresentInNamespaces(List.of("a", "b"), "service-wiremock", "service-wiremock");
		String logs = logs();
		Assertions.assertTrue(logs.contains("using selective namespaces : [a]"));
		Assertions.assertTrue(logs.contains("reading pod in namespace : default"));
		Assertions.assertTrue(logs.contains("registering lister (for services) in namespace : a"));
		Assertions.assertTrue(logs.contains("registering lister (for endpoints) in namespace : a"));

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();
		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();
		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient wiremockInNamespaceAClient = builder().baseUrl("http://localhost/service-instances/service-wiremock")
				.build();

		List<DefaultKubernetesServiceInstance> wiremockInNamespaceA = wiremockInNamespaceAClient.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(wiremockInNamespaceA.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = wiremockInNamespaceA.get(0);
		Assertions.assertEquals(serviceInstance.getNamespace(), "a");

		// enforces this :
		// https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1286
		WebClient clientForNonExistentService = builder().baseUrl("http://localhost/service-instances/non-existent")
				.build();
		List<ServiceInstance> resultForNonExistentService = clientForNonExistentService.method(HttpMethod.GET)
				.retrieve().bodyToMono(new ParameterizedTypeReference<List<ServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(resultForNonExistentService.size(), 0);

		util.wiremock(NAMESPACE_A, "/wiremock", Phase.DELETE);
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.DELETE);
		util.deleteClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A));
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	@Test
	@Order(4)
	void testSimplePodMetadata() {
		util.setUp(NAMESPACE);
		String imageName = "docker.io/springcloud/spring-cloud-kubernetes-client-discovery-it:" + Commons.pomVersion();
		KubernetesClientDiscoveryClientUtils.patchForPodMetadata(imageName, DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));
		new KubernetesClientDiscoveryPodMetadataITDelegate().testSimple();
	}

	@Test
	@Order(5)
	void filterMatchesOneNamespaceViaThePredicate() {
		String imageName = "docker.io/springcloud/spring-cloud-kubernetes-client-discovery-it:" + Commons.pomVersion();
		KubernetesClientDiscoveryClientUtils.patchForUATNamespacesTests(imageName, DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));
		new KubernetesClientDiscoveryFilterITDelegate().filterMatchesOneNamespaceViaThePredicate(util);

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
	@Order(6)
	void filterMatchesBothNamespacesViaThePredicate() {

		// patch the deployment to change what namespaces are take into account
		KubernetesClientDiscoveryClientUtils.patchForTwoNamespacesMatchViaThePredicate(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		new KubernetesClientDiscoveryFilterITDelegate().filterMatchesBothNamespacesViaThePredicate(util);
	}

	@Test
	@Order(7)
	void testBlockingConfiguration() {

		// filter tests are done, clean-up a bit to prepare everything for health tests
		deleteNamespacesAndWiremock();

		String imageName = "docker.io/springcloud/spring-cloud-kubernetes-client-discovery-it:" + Commons.pomVersion();
		KubernetesClientDiscoveryClientUtils.patchForBlockingHealth(imageName, DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		new KubernetesClientDiscoveryHealthITDelegate().testBlockingConfiguration(K3S);
	}

	@Test
	@Order(8)
	void testReactiveConfiguration() {

		KubernetesClientDiscoveryClientUtils.patchForReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		new KubernetesClientDiscoveryHealthITDelegate().testReactiveConfiguration(util, K3S);
	}

	@Test
	@Order(9)
	void testDefaultConfiguration() {

		KubernetesClientDiscoveryClientUtils.patchForBlockingAndReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		new KubernetesClientDiscoveryHealthITDelegate().testDefaultConfiguration(util, K3S);
	}

	private void deleteNamespacesAndWiremock() {
		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.DELETE);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.DELETE);
		util.deleteNamespace(NAMESPACE_A_UAT);
		util.deleteNamespace(NAMESPACE_B_UAT);
	}

	private static void manifests(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("kubernetes-discovery-deployment.yaml");
		V1Service service = (V1Service) util.yaml("kubernetes-discovery-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("kubernetes-discovery-ingress.yaml");

		if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			return;
		}

		if (phase.equals(Phase.CREATE)) {

			List<V1EnvVar> envVars = new ArrayList<>(
					Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
							.orElse(List.of()));
			V1EnvVar debugLevel = new V1EnvVar()
					.name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY").value("DEBUG");

			V1EnvVar debugLevelForClient = new V1EnvVar()
					.name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT").value("DEBUG");

			envVars.add(debugLevel);
			envVars.add(debugLevelForClient);
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private String logs() {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			return execResult.getStdout();
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
