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

package org.springframework.cloud.kubernetes.client.config.it;

import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;
import reactor.netty.http.client.HttpClient;

import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
class ConfigMapAndSecretIT {

	private static final String SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-config-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_NAME = "spring-cloud-kubernetes-client-config-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-client-config-it";

	private static final String NAMESPACE = "default";

	private static final String APP_NAME = "spring-cloud-kubernetes-client-config-it";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.10-k3s1"))
			.withFileSystemBind("/tmp/images", "/tmp/images", BindMode.READ_WRITE).withExposedPorts(80, 6443)
			.withCommand("server") // otherwise, traefik is not installed
			.withReuse(true);

	@BeforeAll
	static void setup() throws Exception {
		K3S.start();
		K3S.execInContainer("ctr", "i", "import", "/tmp/images/spring-cloud-kubernetes-client-config-it.tar");
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
				"docker.io/springcloud/spring-cloud-kubernetes-client-config-it:" + getPomVersion());
	}

	@AfterEach
	void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + K8S_CONFIG_CLIENT_IT_NAME, null, null, null, null, null, null, null, null, null);
		api.deleteNamespacedService(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedConfigMap(APP_NAME, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedSecret(APP_NAME, NAMESPACE, null, null, null, null, null, null);
	}

	@Test
	void testConfigMapAndSecretWatchRefresh() throws Exception {
		deployConfigK8sClientIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		testConfigMapAndSecretRefresh();
	}

	@Disabled
	@Test
	void testConfigMapAndSecretPollingRefresh() throws Exception {
		deployConfigK8sClientPollingIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		testConfigMapAndSecretRefresh();
	}

	void testConfigMapAndSecretRefresh() throws Exception {

		String propertyURL = "http://127.0.0.1:" + K3S.getMappedPort(80) + "/myProperty";
		String secretURL = "http://127.0.0.1:" + K3S.getMappedPort(80) + "/mySecret";

		WebClient.Builder builder = builder();
		WebClient propertyClient = builder.baseUrl(propertyURL).build();

		System.out.println("==== will hit : " + propertyURL);
		try {
			String property = propertyClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block();
			assertThat(property).isEqualTo("from-config-map");
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		WebClient secretClient = builder.baseUrl(secretURL).build();
		String secret = secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block();
		assertThat(secret).isEqualTo("p455w0rd");

		V1ConfigMap configMap = getConfigK8sClientItConfigMap();
		Map<String, String> data = configMap.getData();
		data.replace("application.yaml", data.get("application.yaml").replace("from-config-map", "from-unit-test"));
		configMap.data(data);
		api.replaceNamespacedConfigMap(APP_NAME, NAMESPACE, configMap, null, null, null);
		Awaitility.await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2))
				.until(() -> propertyClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block()
						.equals("from-unit-test"));
		V1Secret v1Secret = getConfigK8sClientItCSecret();
		Map<String, byte[]> secretData = v1Secret.getData();
		secretData.replace("my.config.mySecret", "p455w1rd".getBytes());
		v1Secret.setData(secretData);
		api.replaceNamespacedSecret(APP_NAME, NAMESPACE, v1Secret, null, null, null);
		Awaitility.await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> secretClient
				.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block().equals("p455w1rd"));
	}

	private static void deployConfigK8sClientIt() throws Exception {
		k8SUtils.waitForDeploymentToBeDeleted(K8S_CONFIG_CLIENT_IT_NAME, NAMESPACE);
		api.createNamespacedSecret(NAMESPACE, getConfigK8sClientItCSecret(), null, null, null);
		api.createNamespacedConfigMap(NAMESPACE, getConfigK8sClientItConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigK8sClientItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigK8sClientItService(), null, null, null);

		V1Ingress ingress = getConfigK8sClientItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private static void deployConfigK8sClientPollingIt() throws Exception {
		k8SUtils.waitForDeploymentToBeDeleted(K8S_CONFIG_CLIENT_IT_NAME, NAMESPACE);
		api.createNamespacedSecret(NAMESPACE, getConfigK8sClientItCSecret(), null, null, null);
		api.createNamespacedConfigMap(NAMESPACE, getConfigK8sClientItConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigK8sClientItPollingDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigK8sClientItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getConfigK8sClientItIngress(), null, null, null);
	}

	private static V1Deployment getConfigK8sClientItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Deployment getConfigK8sClientItPollingDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-polling-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Service getConfigK8sClientItService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-service.yaml");
	}

	private static V1Ingress getConfigK8sClientItIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-ingress.yaml");
	}

	private static V1ConfigMap getConfigK8sClientItConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-configmap.yaml");
	}

	private static V1Secret getConfigK8sClientItCSecret() throws Exception {
		return (V1Secret) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-secret.yaml");
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

}
