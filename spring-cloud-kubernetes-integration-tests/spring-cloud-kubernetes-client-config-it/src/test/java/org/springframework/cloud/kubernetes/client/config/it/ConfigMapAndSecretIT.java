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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Ryan Baxter
 */
public class ConfigMapAndSecretIT {

	private static final Log LOG = LogFactory.getLog(ConfigMapAndSecretIT.class);

	private static final String SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-config-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_NAME = "spring-cloud-kubernetes-client-config-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-client-config-it";

	private static final String NAMESPACE = "default";

	private static final String MYPROPERTY_URL = "http://localhost:80/client-config-it/myProperty";

	private static final String MYSECRET_URL = "http://localhost:80/client-config-it/mySecret";

	private static final String APP_NAME = "spring-cloud-kubernetes-client-config-it";

	private static ApiClient client;

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1beta1Api networkingApi;

	private static K8SUtils k8SUtils;

	@BeforeClass
	public static void setup() throws Exception {
		client = createApiClient();
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1beta1Api();
		k8SUtils = new K8SUtils(api, appsApi);
	}

	@After
	public void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + K8S_CONFIG_CLIENT_IT_NAME, null, null, null, null, null, null, null, null, null);
		api.deleteNamespacedService(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedConfigMap(APP_NAME, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedSecret(APP_NAME, NAMESPACE, null, null, null, null, null, null);
	}

	public void testConfigMapAndSecretRefresh() throws Exception {

		RestTemplate rest = new RestTemplateBuilder().build();
		rest.setErrorHandler(new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse clientHttpResponse) throws IOException {
				LOG.warn("Received response status code: " + clientHttpResponse.getRawStatusCode());
				if (clientHttpResponse.getRawStatusCode() == 503) {
					return false;
				}
				return true;
			}

			@Override
			public void handleError(ClientHttpResponse clientHttpResponse) throws IOException {

			}
		});

		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForEntity(MYPROPERTY_URL, String.class).getStatusCode().is2xxSuccessful());

		String myProperty = rest.getForObject(MYPROPERTY_URL, String.class);
		assertThat(myProperty).isEqualTo("from-config-map");
		String mySecret = rest.getForObject(MYSECRET_URL, String.class);
		assertThat(mySecret).isEqualTo("p455w0rd");

		V1ConfigMap configMap = getConfigK8sClientItConfigMap();
		Map<String, String> data = configMap.getData();
		data.replace("application.yaml", data.get("application.yaml").replace("from-config-map", "from-unit-test"));
		configMap.data(data);
		api.replaceNamespacedConfigMap(APP_NAME, NAMESPACE, configMap, null, null, null);
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForObject(MYPROPERTY_URL, String.class).equals("from-unit-test"));
		myProperty = rest.getForObject(MYPROPERTY_URL, String.class);
		assertThat(myProperty).isEqualTo("from-unit-test");

		V1Secret secret = getConfigK8sClientItCSecret();
		Map<String, byte[]> secretData = secret.getData();
		secretData.replace("my.config.mySecret", "p455w1rd".getBytes());
		secret.setData(secretData);
		api.replaceNamespacedSecret(APP_NAME, NAMESPACE, secret, null, null, null);
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForObject(MYSECRET_URL, String.class).equals("p455w1rd"));
		mySecret = rest.getForObject(MYSECRET_URL, String.class);
		assertThat(mySecret).isEqualTo("p455w1rd");

	}

	@Test
	public void testConfigMapAndSecretWatchRefresh() throws Exception {
		deployConfigK8sClientIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		testConfigMapAndSecretRefresh();
	}

	@Test
	public void testConfigMapAndSecretPollingRefresh() throws Exception {
		deployConfigK8sClientPollingIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_CLIENT_CONFIG_IT_DEPLOYMENT_NAME, NAMESPACE);
		testConfigMapAndSecretRefresh();
	}

	private static void deployConfigK8sClientIt() throws Exception {
		k8SUtils.waitForDeploymentToBeDeleted(K8S_CONFIG_CLIENT_IT_NAME, NAMESPACE);
		api.createNamespacedSecret(NAMESPACE, getConfigK8sClientItCSecret(), null, null, null);
		api.createNamespacedConfigMap(NAMESPACE, getConfigK8sClientItConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigK8sClientItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigK8sClientItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getConfigK8sClientItIngress(), null, null, null);
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
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Deployment getConfigK8sClientItPollingDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-polling-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private static V1Service getConfigK8sClientItService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-service.yaml");
		return service;
	}

	private static NetworkingV1beta1Ingress getConfigK8sClientItIngress() throws Exception {
		NetworkingV1beta1Ingress ingress = (NetworkingV1beta1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-ingress.yaml");
		return ingress;
	}

	private static V1ConfigMap getConfigK8sClientItConfigMap() throws Exception {
		V1ConfigMap configmap = (V1ConfigMap) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-configmap.yaml");
		return configmap;
	}

	private static V1Secret getConfigK8sClientItCSecret() throws Exception {
		V1Secret secret = (V1Secret) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-config-it-secret.yaml");
		return secret;
	}

}
