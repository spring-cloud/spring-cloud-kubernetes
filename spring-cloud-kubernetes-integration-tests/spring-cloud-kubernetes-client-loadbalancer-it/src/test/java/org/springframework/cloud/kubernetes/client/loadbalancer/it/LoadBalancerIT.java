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

import java.time.Duration;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
class LoadBalancerIT {

	private static final Log LOG = LogFactory.getLog(LoadBalancerIT.class);

	private static final String WIREMOCK_DEPLOYMENT_NAME = "servicea-wiremock-deployment";

	private static final String WIREMOCK_APP_NAME = "servicea-wiremock";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-loadbalancer-it-deployment";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-client-loadbalancer-it";

	private static final String NAMESPACE = "default";

	private CoreV1Api api;

	private AppsV1Api appsApi;

	private NetworkingV1Api networkingApi;

	private K8SUtils k8SUtils;

	private static final K3sContainer K3S = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.10-k3s1"))
			.withFileSystemBind("/tmp/images", "/tmp/images", BindMode.READ_WRITE).withExposedPorts(80, 6443)
			.withCommand("server") // otherwise, traefik is not installed
			.withReuse(true);

	@BeforeEach
	void setup() throws Exception {
		K3S.start();
		K3S.execInContainer("ctr", "i", "import", "/tmp/images/spring-cloud-kubernetes-client-loadbalancer-it.tar");
		K3S.execInContainer("ctr", "i", "import", "/tmp/images/wiremock-wiremock:2.32.0.tar");
		createApiClient(K3S.getKubeConfigYaml());
		this.api = new CoreV1Api();
		this.appsApi = new AppsV1Api();
		this.networkingApi = new NetworkingV1Api();
		this.k8SUtils = new K8SUtils(api, appsApi);

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(WIREMOCK_APP_NAME, NAMESPACE);

		RbacAuthorizationV1Api rbacApi = new RbacAuthorizationV1Api();
		api.createNamespacedServiceAccount(NAMESPACE, getConfigK8sClientItServiceAccount(), null, null, null);
		rbacApi.createNamespacedRoleBinding(NAMESPACE, getConfigK8sClientItRoleBinding(), null, null, null);
		rbacApi.createNamespacedRole(NAMESPACE, getConfigK8sClientItRole(), null, null, null);

	}

	@AfterAll
	static void afterAll() throws Exception {
		K3S.execInContainer("crictl", "rmi",
				"docker.io/springcloud/spring-cloud-kubernetes-client-loadbalancer-it:" + getPomVersion());
		K3S.execInContainer("crictl", "rmi", "docker.io/wiremock/wiremock:2.32.0");
	}

	@AfterEach
	void afterEach() throws Exception {
		cleanup();
	}

	@Test
	void testLoadBalancerServiceMode() throws Exception {
		deployLoadbalancerServiceIt();
		testLoadBalancer();
	}

	@Test
	void testLoadBalancerPodMode() throws Exception {
		deployLoadbalancerPodIt();
		testLoadBalancer();
	}

	private void cleanup() throws ApiException {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() {
		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, NAMESPACE);

		String serviceURL = "localhost:" + K3S.getMappedPort(80) + "/servicea";
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(serviceURL).build();

		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, String.class);
		@SuppressWarnings("unchecked")
		Map<String, String> result = (Map<String, String>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Assertions.assertThat(result.containsKey("mappings")).isTrue();
		Assertions.assertThat(result.containsKey("meta")).isTrue();

	}

	@AfterEach
	void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null, null);

		api.deleteNamespacedService(WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("wiremock-ingress", NAMESPACE, null, null, null, null, null, null);

	}

	private void deployLoadbalancerServiceIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getLoadbalancerServiceItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getLoadbalancerItService(), null, null, null);

		V1Ingress ingress = getLoadbalancerItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private void deployLoadbalancerPodIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getLoadbalancerPodItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getLoadbalancerItService(), null, null, null);

		V1Ingress ingress = getLoadbalancerItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private V1Deployment getLoadbalancerServiceItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-service-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Deployment getLoadbalancerPodItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-service-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private void deployWiremock() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getWiremockDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getWiremockAppService(), null, null, null);

		V1Ingress ingress = getWiremockIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private V1Ingress getLoadbalancerItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-ingress.yaml");
	}

	private V1Service getLoadbalancerItService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-service.yaml");
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
