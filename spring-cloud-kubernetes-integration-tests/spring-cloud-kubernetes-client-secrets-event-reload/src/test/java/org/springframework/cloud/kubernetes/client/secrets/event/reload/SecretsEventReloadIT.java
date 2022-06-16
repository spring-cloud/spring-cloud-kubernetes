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

package org.springframework.cloud.kubernetes.client.secrets.event.reload;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author wind57
 */
class SecretsEventReloadIT {

	private static final String PROPERTY_URL = "localhost:80/key";

	private static final String SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-secrets-deployment-event-reload";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-client-secrets-event-reload";

	private static final String NAMESPACE = "default";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void setup() throws Exception {
		K3S.start();
		Commons.validateImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		k8SUtils.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
	}

	@AfterEach
	void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		api.deleteNamespacedService(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("spring-cloud-kubernetes-client-secrets-ingress-event-reload", NAMESPACE,
				null, null, null, null, null, null);
		api.deleteNamespacedSecret("event-reload", NAMESPACE, null, null, null, null, null, null);
	}

	@Test
	void testSecretReload() throws Exception {
		deployConfigK8sClientIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		testSecretEventReload();
	}

	void testSecretEventReload() throws Exception {

		WebClient.Builder builder = builder();
		WebClient secretClient = builder.baseUrl(PROPERTY_URL).build();
		String secret = secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		assertThat(secret).isEqualTo("initial");

		V1Secret v1Secret = getConfigK8sClientItCSecret();
		Map<String, byte[]> secretData = v1Secret.getData();
		secretData.replace("application.properties", "from.properties.key: after-change".getBytes());
		v1Secret.setData(secretData);
		api.replaceNamespacedSecret("event-reload", NAMESPACE, v1Secret, null, null, null);

		Awaitility.await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2))
				.until(() -> secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
						.retryWhen(retrySpec()).block().equals("after-change"));
	}

	private static void deployConfigK8sClientIt() throws Exception {
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		api.createNamespacedSecret(NAMESPACE, getConfigK8sClientItCSecret(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigK8sClientItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigK8sClientItService(), null, null, null);

		V1Ingress ingress = getConfigK8sClientItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private static V1Deployment getConfigK8sClientItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath("deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Service getConfigK8sClientItService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("service.yaml");
	}

	private static V1Ingress getConfigK8sClientItIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("ingress.yaml");
	}

	private static V1Secret getConfigK8sClientItCSecret() throws Exception {
		return (V1Secret) K8SUtils.readYamlFromClasspath("secret.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
