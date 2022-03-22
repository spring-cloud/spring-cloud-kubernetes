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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
class ReactiveDiscoveryClientIT {

	private static final String WIREMOCK_DEPLOYMENT_NAME = "servicea-wiremock-deployment";

	private static final String WIREMOCK_APP_NAME = "servicea-wiremock";

	private static final String SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-reactive-discovery-it-deployment";

	private static final String SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME = "spring-cloud-kubernetes-client-reactive-discovery-it";

	private static final String NAMESPACE = "default";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.10-k3s1"))
			.withFileSystemBind("/tmp/images", "/tmp/images", BindMode.READ_WRITE).withExposedPorts(80, 6443)
			.withCommand("server") // otherwise, traefik is not installed
			.withReuse(true);

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		K3S.execInContainer("ctr", "i", "import",
				"/tmp/images/spring-cloud-kubernetes-client-reactive-discoveryclient-it.tar");
		K3S.execInContainer("ctr", "i", "import", "/tmp/images/wiremock-wiremock:2.32.0.tar");
		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);

		RbacAuthorizationV1Api rbacApi = new RbacAuthorizationV1Api();
		api.createNamespacedServiceAccount(NAMESPACE, getConfigK8sClientItServiceAccount(), null, null, null);
		rbacApi.createNamespacedRoleBinding(NAMESPACE, getConfigK8sClientItRoleBinding(), null, null, null);
		rbacApi.createNamespacedRole(NAMESPACE, getConfigK8sClientItRole(), null, null, null);
	}

	@AfterAll
	static void afterAll() throws Exception {
		K3S.execInContainer("crictl", "rmi",
				"docker.io/springcloud/spring-cloud-kubernetes-client-reactive-discoveryclient-it:" + getPomVersion());
		K3S.execInContainer("crictl", "rmi", "docker.io/wiremock/wiremock:2.32.0");
	}

	@BeforeEach
	void setup() throws Exception {

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(WIREMOCK_APP_NAME, NAMESPACE);

	}

	@AfterEach
	void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null, null);

		api.deleteNamespacedService(WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("wiremock-ingress", NAMESPACE, null, null, null, null, null, null);

		cleanup();
	}

	@Test
	void testReactiveDiscoveryClient() throws Exception {
		deployReactiveDiscoveryIt();
		testLoadBalancer();
		testHealth();
	}

	@SuppressWarnings("unchecked")
	private void testHealth() {

		String heathURL = "localhost:" + K3S.getMappedPort(80) + "/reactive-discovery-it/actuator/health";
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(heathURL).build();
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> health = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> components = (Map<String, Object>) health.get("components");

		assertThat(components.containsKey("reactiveDiscoveryClients")).isTrue();
		Map<String, Object> discoveryComposite = (Map<String, Object>) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");
	}

	private void cleanup() throws ApiException {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME, null, null, null, null, null,
				null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME, NAMESPACE, null, null, null, null,
				null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() {

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME, NAMESPACE);

		String services = "localhost:" + K3S.getMappedPort(80) + "/reactive-discovery-it/services";
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(services).build();
		String servicesResponse = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions
				.assertThat(Arrays.stream(servicesResponse.split(",")).anyMatch("servicea-wiremock"::equalsIgnoreCase))
				.isTrue();

	}

	private void deployIngress(V1Ingress ingress) throws Exception {
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private void deployReactiveDiscoveryIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getReactiveDiscoveryItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getReactiveDiscoveryService(), null, null, null);
		deployIngress(getReactiveDiscoveryItIngress());
	}

	private V1Deployment getReactiveDiscoveryItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getReactiveDiscoveryService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-service.yaml");
	}

	private V1Ingress getReactiveDiscoveryItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-ingress.yaml");
	}

	private void deployWiremock() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getWiremockDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getWiremockAppService(), null, null, null);
		deployIngress(getWiremockIngress());
	}

	private V1Ingress getWiremockIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("wiremock-ingress.yaml");
	}

	private V1Service getWiremockAppService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("wiremock-service.yaml");
	}

	private V1Deployment getWiremockDeployment() throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath("wiremock-deployment.yaml");
	}

	private static V1ServiceAccount getConfigK8sClientItServiceAccount() throws Exception {
		return (V1ServiceAccount) K8SUtils.readYamlFromClasspath("service-account.yaml");
	}

	private static V1RoleBinding getConfigK8sClientItRoleBinding() throws Exception {
		return (V1RoleBinding) K8SUtils.readYamlFromClasspath("role-binding.yaml");
	}

	private static V1Role getConfigK8sClientItRole() throws Exception {
		return (V1Role) K8SUtils.readYamlFromClasspath("role.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
