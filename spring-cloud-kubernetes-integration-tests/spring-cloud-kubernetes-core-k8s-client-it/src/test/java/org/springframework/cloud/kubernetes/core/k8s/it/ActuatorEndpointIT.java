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

package org.springframework.cloud.kubernetes.core.k8s.it;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
class ActuatorEndpointIT {

	private static final String SPRING_CLOUD_K8S_CLIENT_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-core-k8s-client-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_NAME = "spring-cloud-kubernetes-core-k8s-client-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-core-k8s-client-it";

	private static final String NAMESPACE = "default";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static K8SUtils k8SUtils;

	private static NetworkingV1Api networkingApi;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		Commons.loadImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		k8SUtils.setUp(NAMESPACE);

		deployCoreK8sClientIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CLIENT_IT_DEPLOYMENT_NAME, NAMESPACE);
	}

	@AfterAll
	static void after() throws Exception {
		Commons.cleanUp(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + K8S_CONFIG_CLIENT_IT_NAME, null, null, null, null, null, null, null, null, null);
		api.deleteNamespacedService(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testHealth() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/actuator/health").build();

		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> health = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> components = (Map<String, Object>) health.get("components");
		assertThat(components.containsKey("kubernetes")).isTrue();
		Map<String, Object> kubernetes = (Map<String, Object>) components.get("kubernetes");
		assertThat(kubernetes.get("status")).isEqualTo("UP");
		Map<String, Object> details = (Map<String, Object>) kubernetes.get("details");
		assertThat(details.containsKey("hostIp")).isTrue();
		assertThat(details.containsKey("inside")).isTrue();
		assertThat(details.containsKey("labels")).isTrue();
		assertThat(details.containsKey("namespace")).isTrue();
		assertThat(details.containsKey("nodeName")).isTrue();
		assertThat(details.containsKey("podIp")).isTrue();
		assertThat(details.containsKey("podName")).isTrue();
		assertThat(details.containsKey("serviceAccount")).isTrue();

		assertThat(components.containsKey("discoveryComposite")).isTrue();
		Map<String, Object> discoveryComposite = (Map<String, Object>) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");

	}

	@Test
	@SuppressWarnings("unchecked")
	void testInfo() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/actuator/info").build();

		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> info = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> kubernetes = (Map<String, Object>) info.get("kubernetes");
		assertThat(kubernetes.containsKey("hostIp")).isTrue();
		assertThat(kubernetes.containsKey("inside")).isTrue();
		assertThat(kubernetes.containsKey("namespace")).isTrue();
		assertThat(kubernetes.containsKey("nodeName")).isTrue();
		assertThat(kubernetes.containsKey("podIp")).isTrue();
		assertThat(kubernetes.containsKey("podName")).isTrue();
		assertThat(kubernetes.containsKey("serviceAccount")).isTrue();
	}

	private static void deployCoreK8sClientIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getCoreK8sClientItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getCoreK8sClientItService(), null, null, null);

		V1Ingress ingress = getCoreK8sClientItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private static V1Deployment getCoreK8sClientItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Service getCoreK8sClientItService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-service.yaml");
	}

	private static V1Ingress getCoreK8sClientItIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-ingress.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
