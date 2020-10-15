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

import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * @author Ryan Baxter
 */
@RunWith(MockitoJUnitRunner.class)
public class ActuatorEndpointIT {

	private static final String KIND_REPO_HOST_PORT = "localhost:5000";

	private static final String KIND_REPO_URL = "http://" + KIND_REPO_HOST_PORT;

	private static final String IMAGE = "spring-cloud-kubernetes-core-k8s-client-it";

	private static final String IMAGE_TAG = "2.0.0-SNAPSHOT";

	private static final String LOCAL_REPO = "docker.io/springcloud";

	private static final String LOCAL_IMAGE = LOCAL_REPO + "/" + IMAGE + ":" + IMAGE_TAG;

	private static final String KIND_IMAGE = KIND_REPO_HOST_PORT + "/" + IMAGE;

	private static final String KIND_IMAGE_WITH_TAG = KIND_IMAGE + ":" + IMAGE_TAG;

	private static final String SPRING_CLOUD_K8S_CLIENT_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-core-k8s-client-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_NAME = "spring-cloud-kubernetes-core-k8s-client-it-deployment";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-core-k8s-client-it";

	private static final String NAMESPACE = "default";

	private static ApiClient client;

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1beta1Api networkingApi;

	private static K8SUtils k8SUtils;

	@BeforeClass
	public static void setup() throws Exception {
		client = Config.defaultClient();
		// client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1beta1Api();
		k8SUtils = new K8SUtils(api, appsApi);

		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withRegistryUrl(KIND_REPO_URL).build();
		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig()).build();

		DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
		dockerClient.tagImageCmd(LOCAL_IMAGE, KIND_IMAGE, IMAGE_TAG).exec();
		dockerClient.pushImageCmd(KIND_IMAGE_WITH_TAG).start();

		deployCoreK8sClientIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CLIENT_IT_DEPLOYMENT_NAME, NAMESPACE);
	}

	private static void deployCoreK8sClientIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getCoreK8sClientItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getCoreK8sClientItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getCoreK8sClientItIngress(), null, null, null);
	}

	private static V1Deployment getCoreK8sClientItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-deployment.yaml");
		return deployment;
	}

	private static V1Service getCoreK8sClientItService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-service.yaml");
		return service;
	}

	private static NetworkingV1beta1Ingress getCoreK8sClientItIngress() throws Exception {
		NetworkingV1beta1Ingress ingress = (NetworkingV1beta1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-core-k8s-client-it-ingress.yaml");
		return ingress;
	}

	@Test
	public void testHealth() {
		RestTemplate rest = new RestTemplateBuilder().build();

		Map<String, Object> health = rest.getForObject("http://localhost:80/core-k8s-client-it/actuator/health",
				Map.class);
		Map<String, Object> components = (Map) health.get("components");
		assertThat(components.containsKey("kubernetes")).isTrue();
		Map<String, Object> kubernetes = (Map) components.get("kubernetes");
		assertThat(kubernetes.get("status")).isEqualTo("UP");
		Map<String, Object> details = (Map) kubernetes.get("details");
		assertThat(details.containsKey("hostIp")).isTrue();
		assertThat(details.containsKey("inside")).isTrue();
		assertThat(details.containsKey("labels")).isTrue();
		assertThat(details.containsKey("namespace")).isTrue();
		assertThat(details.containsKey("nodeName")).isTrue();
		assertThat(details.containsKey("podIp")).isTrue();
		assertThat(details.containsKey("podName")).isTrue();
		assertThat(details.containsKey("serviceAccount")).isTrue();
	}

	@Test
	public void testInfo() {
		RestTemplate rest = new RestTemplateBuilder().build();

		Map<String, Object> info = rest.getForObject("http://localhost:80/core-k8s-client-it/actuator/info", Map.class);
		Map<String, Object> kubernetes = (Map) info.get("kubernetes");
		assertThat(kubernetes.containsKey("hostIp")).isTrue();
		assertThat(kubernetes.containsKey("inside")).isTrue();
		assertThat(kubernetes.containsKey("namespace")).isTrue();
		assertThat(kubernetes.containsKey("nodeName")).isTrue();
		assertThat(kubernetes.containsKey("podIp")).isTrue();
		assertThat(kubernetes.containsKey("podName")).isTrue();
		assertThat(kubernetes.containsKey("serviceAccount")).isTrue();
	}

	@AfterClass
	public static void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + K8S_CONFIG_CLIENT_IT_NAME, null, null, null, null, null, null, null, null);
		api.deleteNamespacedService(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

}
