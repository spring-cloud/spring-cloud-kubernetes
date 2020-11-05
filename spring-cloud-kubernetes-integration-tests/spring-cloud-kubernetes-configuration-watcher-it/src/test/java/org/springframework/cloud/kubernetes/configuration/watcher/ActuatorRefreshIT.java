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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;

/**
 * @author Ryan Baxter
 */
@RunWith(MockitoJUnitRunner.class)
public class ActuatorRefreshIT {

	private static final String KIND_REPO_HOST_PORT = "localhost:5000";

	private static final String KIND_REPO_URL = "http://" + KIND_REPO_HOST_PORT;

	private static final String IMAGE = "spring-cloud-kubernetes-configuration-watcher";

	private static final String IMAGE_TAG = "2.0.0-SNAPSHOT";

	private static final String LOCAL_REPO = "docker.io/springcloud";

	private static final String LOCAL_IMAGE = LOCAL_REPO + "/" + IMAGE + ":" + IMAGE_TAG;

	private static final String KIND_IMAGE = KIND_REPO_HOST_PORT + "/" + IMAGE;

	private static final String KIND_IMAGE_WITH_TAG = KIND_IMAGE + ":" + IMAGE_TAG;

	private static final String CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME = "config-watcher-wiremock-deployment";

	private static final String CONFIG_WATCHER_WIREMOCK_APP_NAME = "config-watcher-wiremock";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String WIREMOCK_HOST = "localhost";

	private static final String WIREMOCK_PATH = "/wiremock";

	private static final int WIREMOCK_PORT = 80;

	private static final String NAMESPACE = "default";

	private ApiClient client;

	private CoreV1Api api;

	private AppsV1Api appsApi;

	private NetworkingV1beta1Api networkingApi;

	private K8SUtils k8SUtils;

	@Before
	public void setup() throws Exception {
		this.client = Config.defaultClient();
		// client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
		this.api = new CoreV1Api();
		this.appsApi = new AppsV1Api();
		this.networkingApi = new NetworkingV1beta1Api();
		this.k8SUtils = new K8SUtils(api, appsApi);

		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withRegistryUrl(KIND_REPO_URL).build();
		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig()).build();

		DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
		dockerClient.tagImageCmd(LOCAL_IMAGE, KIND_IMAGE, IMAGE_TAG).exec();
		dockerClient.pushImageCmd(KIND_IMAGE_WITH_TAG).start();

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE);

		deployConfigWatcher();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
	}

	@Test
	public void testActuatorRefresh() throws Exception {
		// Configure wiremock to point at the server
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT, WIREMOCK_PATH);

		// Setup stubs for actuator refresh
		stubFor(post(urlEqualTo("/actuator/refresh")).willReturn(aResponse().withStatus(200)));

		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName(CONFIG_WATCHER_WIREMOCK_APP_NAME)
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata().addToData("foo", "bar").build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);

		// Wait a bit before we verify
		await().atMost(Duration.ofMillis(3400))
				.until(() -> !findAll(postRequestedFor(urlEqualTo("/actuator/refresh"))).isEmpty());

		verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	@After
	public void after() throws Exception {

		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null);
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null,
				null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedService(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("nginx-ingress", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedConfigMap(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedConfigMap(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
	}

	private void deployConfigWatcher() throws Exception {
		api.createNamespacedConfigMap(NAMESPACE, getConfigWatcherConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null);
	}

	private V1Service getConfigWatcherService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-service.yaml");
		return service;
	}

	private V1ConfigMap getConfigWatcherConfigMap() throws Exception {
		V1ConfigMap configMap = (V1ConfigMap) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
		return configMap;
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-http-deployment.yaml");
		return deployment;
	}

	private void deployWiremock() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getWireockDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getWiremockAppService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getWiremockIngress(), null, null, null);
	}

	private NetworkingV1beta1Ingress getWiremockIngress() throws Exception {
		NetworkingV1beta1Ingress ingress = (NetworkingV1beta1Ingress) k8SUtils
				.readYamlFromClasspath("wiremock-ingress.yaml");
		return ingress;
	}

	private V1Service getWiremockAppService() throws Exception {
		V1Service service = (V1Service) k8SUtils.readYamlFromClasspath("wiremock-service.yaml");
		return service;
	}

	private V1Deployment getWireockDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils.readYamlFromClasspath("wiremock-deployment.yaml");
		return deployment;
	}

}
