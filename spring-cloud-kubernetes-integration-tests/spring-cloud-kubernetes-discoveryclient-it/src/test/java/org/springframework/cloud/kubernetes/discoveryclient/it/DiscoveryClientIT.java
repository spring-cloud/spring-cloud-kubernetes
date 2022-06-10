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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
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
class DiscoveryClientIT {

	private static final Log LOG = LogFactory.getLog(DiscoveryClientIT.class);

	private static final String DISCOVERY_SERVER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-discoveryserver-deployment";

	private static final String DISCOVERY_SERVER_APP_NAME = "spring-cloud-kubernetes-discoveryserver";

	private static final String SPRING_CLOUD_K8S_DISCOVERY_CLIENT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-discoveryclient-it-deployment";

	private static final String SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME = "spring-cloud-kubernetes-discoveryclient-it";

	private static final String NAMESPACE = "default";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(DISCOVERY_SERVER_APP_NAME, K3S);

		Commons.validateImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);

		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		k8SUtils.setUp(NAMESPACE);

		deployDiscoveryServer();

		// Check to make sure the discovery server deployment is ready
		k8SUtils.waitForDeployment(DISCOVERY_SERVER_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(DISCOVERY_SERVER_APP_NAME, NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(DISCOVERY_SERVER_APP_NAME, K3S);
		Commons.cleanUp(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, K3S);

		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + DISCOVERY_SERVER_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null,
				null);

		api.deleteNamespacedService(DISCOVERY_SERVER_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("discoveryserver-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	@AfterEach
	void afterEach() throws ApiException {
		cleanup();
	}

	@Test
	void testDiscoveryClient() throws Exception {
		deployDiscoveryIt();
		testLoadBalancer();
		testHealth();
	}

	private void cleanup() throws ApiException {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_DISCOVERY_CLIENT_DEPLOYMENT_NAME, null, null, null, null, null,
				null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() {

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_DISCOVERY_CLIENT_DEPLOYMENT_NAME, NAMESPACE);
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/services").build();

		String[] result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String[].class)
				.retryWhen(retrySpec()).block();
		LOG.info("Services: " + Arrays.toString(result));
		assertThat(Arrays.stream(result).anyMatch("spring-cloud-kubernetes-discoveryserver"::equalsIgnoreCase))
				.isTrue();

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

	private void deployDiscoveryIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getDiscoveryItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getDiscoveryService(), null, null, null);

		V1Ingress ingress = getDiscoveryItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private V1Deployment getDiscoveryItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("client/spring-cloud-kubernetes-discoveryclient-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static void deployDiscoveryServer() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getDiscoveryServerDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getDiscoveryServerService(), null, null, null);

		V1Ingress ingress = getDiscoveryServerIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private static V1Deployment getDiscoveryServerDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("server/spring-cloud-kubernetes-discoveryserver-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Ingress getDiscoveryServerIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("server/spring-cloud-kubernetes-discoveryserver-ingress.yaml");
	}

	private static V1Service getDiscoveryServerService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("server/spring-cloud-kubernetes-discoveryserver-service.yaml");
	}

	private V1Ingress getDiscoveryItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("client/spring-cloud-kubernetes-discoveryclient-it-ingress.yaml");
	}

	private V1Service getDiscoveryService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("client/spring-cloud-kubernetes-discoveryclient-it-service.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
