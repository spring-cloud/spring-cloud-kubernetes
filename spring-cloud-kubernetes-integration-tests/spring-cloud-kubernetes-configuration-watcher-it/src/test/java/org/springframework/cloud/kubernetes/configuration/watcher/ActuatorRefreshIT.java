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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.time.Duration;

import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
class ActuatorRefreshIT {

	private static final String CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME = "config-watcher-wiremock-deployment";

	private static final String CONFIG_WATCHER_WIREMOCK_APP_NAME = "config-watcher-wiremock";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String WIREMOCK_HOST = "localhost";

	private static final String WIREMOCK_PATH = "/wiremock";

	private static final int WIREMOCK_PORT = 80;

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
		K3S.execInContainer("ctr", "i", "import", "/tmp/images/spring-cloud-kubernetes-configuration-watcher-it.tar");
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

	@BeforeEach
	void setup() throws Exception {
		deployWiremock();
		deployConfigWatcher();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);
		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE);
		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
	}

	@Test
	void testActuatorRefresh() throws Exception {
		// Configure wiremock to point at the server
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT, WIREMOCK_PATH);

		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60)).ignoreException(VerificationException.class)
				.until(() -> stubFor(post(urlEqualTo("/actuator/refresh")).willReturn(aResponse().withStatus(200)))
						.getResponse().wasConfigured());

		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName(CONFIG_WATCHER_WIREMOCK_APP_NAME)
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata().addToData("foo", "bar").build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);

		// Wait a bit before we verify
		await().atMost(Duration.ofMillis(3400))
				.until(() -> !findAll(postRequestedFor(urlEqualTo("/actuator/refresh"))).isEmpty());

		verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	@AfterEach
	void after() throws Exception {

		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null,
				null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedService(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("nginx-ingress", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedConfigMap(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedConfigMap(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		// Check to make sure the controller deployment is deleted
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);
	}

	private void deployConfigWatcher() throws Exception {
		api.createNamespacedConfigMap(NAMESPACE, getConfigWatcherConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null);
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(
				"config-watcher/spring-cloud-kubernetes-configuration-watcher-http-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private void deployWiremock() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getWiremockDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getWiremockAppService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getWiremockIngress(), null, null, null);
	}

	private V1Service getConfigWatcherService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");
	}

	private V1ConfigMap getConfigWatcherConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils
				.readYamlFromClasspath("config-watcher/spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
	}

	private V1Ingress getWiremockIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("wiremock/wiremock-ingress.yaml");
	}

	private V1Service getWiremockAppService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("wiremock/wiremock-service.yaml");
	}

	private V1Deployment getWiremockDeployment() throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath("wiremock/wiremock-deployment.yaml");
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

}
