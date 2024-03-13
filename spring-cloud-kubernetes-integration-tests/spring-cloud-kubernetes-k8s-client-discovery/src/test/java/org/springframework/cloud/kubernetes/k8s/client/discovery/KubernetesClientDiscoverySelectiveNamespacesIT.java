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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
class KubernetesClientDiscoverySelectiveNamespacesIT {

	private static final String BLOCKING_PUBLISH = "Will publish InstanceRegisteredEvent from blocking implementation";

	private static final String REACTIVE_PUBLISH = "Will publish InstanceRegisteredEvent from reactive implementation";

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_A = "a";

	private static final String NAMESPACE_B = "b";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-discovery";

	private static final String DEPLOYMENT_NAME = "spring-cloud-kubernetes-k8s-client-discovery";

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);

		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A, NAMESPACE_B));
		util.wiremock(NAMESPACE, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.CREATE, false);
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);

		util.wiremock(NAMESPACE, "/wiremock", Phase.DELETE, false);
		util.wiremock(NAMESPACE_A, "/wiremock", Phase.DELETE, false);
		util.wiremock(NAMESPACE_B, "/wiremock", Phase.DELETE, false);
		util.deleteClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A, NAMESPACE_B));
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
		manifests(Phase.DELETE);
		Commons.systemPrune();
	}

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search only in selective namespace
	 * 'a' with blocking enabled and reactive disabled, as such find a single service and
	 * its service instance.
	 */
	@Test
	@Order(1)
	void testOneNamespaceBlockingOnly() {

		Commons.waitForLogStatement("using selective namespaces : [a]", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a]", K3S,
				IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesPresent : found selective namespaces : [a]", K3S,
				IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", K3S, IMAGE_NAME);

		// this tiny checks makes sure that blocking is enabled and reactive is disabled.
		Commons.waitForLogStatement(BLOCKING_PUBLISH, K3S, IMAGE_NAME);
		Assertions.assertFalse(logs().contains(REACTIVE_PUBLISH));

		blockingCheck();

	}

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search only in selective namespace
	 * 'a' with blocking disabled and reactive enabled, as such find a single service and
	 * its service instance.
	 */
	@Test
	@Order(2)
	void testOneNamespaceReactiveOnly() {

		KubernetesClientDiscoveryClientUtils.patchForReactiveOnly(DEPLOYMENT_NAME, NAMESPACE);

		Commons.waitForLogStatement("using selective namespaces : [a]", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a]", K3S,
				IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", K3S, IMAGE_NAME);

		// this tiny checks makes sure that reactive is enabled and blocking is disabled.
		Commons.waitForLogStatement(REACTIVE_PUBLISH, K3S, IMAGE_NAME);
		Assertions.assertFalse(logs().contains(BLOCKING_PUBLISH));

		reactiveCheck();

	}

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search only in selective namespace
	 * 'a' with blocking enabled and reactive enabled, as such find a single service and
	 * its service instance.
	 */
	@Test
	@Order(3)
	void testOneNamespaceBothBlockingAndReactive() {

		KubernetesClientDiscoveryClientUtils.patchForBlockingAndReactive(DEPLOYMENT_NAME, NAMESPACE);

		Commons.waitForLogStatement("using selective namespaces : [a]", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a]", K3S,
				IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesPresent : found selective namespaces : [a]", K3S,
				IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", K3S, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", K3S, IMAGE_NAME);

		// this tiny checks makes sure that blocking and reactive is enabled.
		Commons.waitForLogStatement(BLOCKING_PUBLISH, K3S, IMAGE_NAME);
		Commons.waitForLogStatement(REACTIVE_PUBLISH, K3S, IMAGE_NAME);

		blockingCheck();
		reactiveCheck();

	}

	/**
	 * previous test already has: <pre>
	 *     - SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0 = a
	 *     - SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED = TRUE
	 *     - SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED = TRUE
	 *
	 *     All we need to patch for is:
	 *     -  add one more namespace to track, via SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1 = b
	 *     - disable reactive, via SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED = FALSE
	 *
	 *    As such, two namespaces + blocking only, is achieved.
	 * </pre>
	 */
	@Test
	@Order(4)
	void testTwoNamespacesBlockingOnly() {
		KubernetesClientDiscoveryClientUtils.patchForTwoNamespacesBlockingOnly(DEPLOYMENT_NAME, NAMESPACE);
		new KubernetesClientDiscoveryMultipleSelectiveNamespacesITDelegate().testTwoNamespacesBlockingOnly(K3S);
	}

	/**
	 * previous test already has: <pre>
	 *     - SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0 = a
	 *     - SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1 = b
	 *     - SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED = FALSE
	 *     - SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED = TRUE
	 *
	 *     We invert the reactive and blocking in this test via patching.
	 *
	 *    As such, two namespaces + reactive only, is achieved.
	 * </pre>
	 */
	@Test
	@Order(5)
	void testTwoNamespacesReactiveOnly() {
		KubernetesClientDiscoveryClientUtils.patchForReactiveOnly(DEPLOYMENT_NAME, NAMESPACE);
		new KubernetesClientDiscoveryMultipleSelectiveNamespacesITDelegate().testTwoNamespaceReactiveOnly(K3S);
	}

	/**
	 * previous test already has: <pre>
	 *     - SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0 = a
	 *     - SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1 = b
	 *     - SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED = TRUE
	 *     - SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED = FALSE
	 *
	 *     We invert the blocking support.
	 *
	 *    As such, two namespaces + blocking and reactive, is achieved.
	 * </pre>
	 */
	@Test
	@Order(6)
	void testTwoNamespacesBothBlockingAndReactive() {
		KubernetesClientDiscoveryClientUtils.patchToAddBlockingSupport(DEPLOYMENT_NAME, NAMESPACE);
		new KubernetesClientDiscoveryMultipleSelectiveNamespacesITDelegate()
				.testTwoNamespacesBothBlockingAndReactive(K3S);
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
			V1EnvVar selectiveNamespaceA = new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0")
					.value(NAMESPACE_A);

			V1EnvVar disableReactiveEnvVar = new V1EnvVar().name("SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED")
					.value("FALSE");
			envVars.add(disableReactiveEnvVar);

			envVars.add(debugLevel);
			envVars.add(selectiveNamespaceA);
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
	}

	private void reactiveCheck() {
		WebClient servicesClient = builder().baseUrl("http://localhost/reactive/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient ourServiceClient = builder().baseUrl("http://localhost/reactive/service-instances/service-wiremock")
				.build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstance.getNamespace(), "a");
	}

	private void blockingCheck() {
		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient ourServiceClient = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstance.getNamespace(), "a");
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

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
