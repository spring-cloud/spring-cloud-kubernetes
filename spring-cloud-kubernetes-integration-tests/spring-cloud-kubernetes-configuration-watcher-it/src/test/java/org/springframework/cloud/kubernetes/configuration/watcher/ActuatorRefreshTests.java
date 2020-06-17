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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.NetworkingV1beta1Api;
import io.kubernetes.client.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.models.NetworkingV1beta1IngressBuilder;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapBuilder;
import io.kubernetes.client.models.V1Deployment;
import io.kubernetes.client.models.V1DeploymentBuilder;
import io.kubernetes.client.models.V1DeploymentList;
import io.kubernetes.client.models.V1Endpoints;
import io.kubernetes.client.models.V1EndpointsList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceBuilder;
import io.kubernetes.client.util.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;

/**
 * @author Ryan Baxter
 */
@RunWith(MockitoJUnitRunner.class)
public class ActuatorRefreshTests {

	private static final String KIND_REPO_HOST_PORT = "localhost:5000";

	private static final String KIND_REPO_URL = "http://" + KIND_REPO_HOST_PORT;

	private static final String IMAGE = "spring-cloud-kubernetes-configuration-watcher";

	private static final String IMAGE_TAG = "2.0.0-SNAPSHOT";

	private static final String LOCAL_REPO = "docker.io/spring-cloud";

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

	private static final String SPRING_CLOUD_KUBERNETES_SERVICE_ACCOUNT_NAME = "spring-cloud-kubernetes-serviceaccount";

	@Before
	public void setup() throws Exception {
		ApiClient client = Config.defaultClient();
		// client.setDebugging(true);
		Configuration.setDefaultApiClient(client);

		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withRegistryUrl(KIND_REPO_URL).build();
		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig())
				.build();

		DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
		dockerClient.tagImageCmd(LOCAL_IMAGE, KIND_IMAGE, IMAGE_TAG).exec();
		dockerClient.pushImageCmd(KIND_IMAGE_WITH_TAG).start();

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		waitForDeployment(CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME);

		// Check to see if endpoint is ready
		waitForEndpointReady(CONFIG_WATCHER_WIREMOCK_APP_NAME);

		deployConfigWatcher();

		// Check to make sure the controller deployment is ready
		waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME);
	}

	@Test
	public void testActuatorRefresh() throws Exception {
		CoreV1Api api = new CoreV1Api();

		// Configure wiremock to point at the server
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT, WIREMOCK_PATH);

		// Setup stubs for actuator refresh
		stubFor(post(urlEqualTo("/actuator/refresh"))
				.willReturn(aResponse().withStatus(200)));

		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
				.withName(CONFIG_WATCHER_WIREMOCK_APP_NAME)
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata()
				.addToData("foo", "bar").build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);

		// Wait a bit before we verify
		await().atMost(Duration.ofMillis(3400))
				.until(() -> !findAll(postRequestedFor(urlEqualTo("/actuator/refresh")))
						.isEmpty());

		verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	@After
	public void after() throws Exception {
		ApiClient client = Config.defaultClient();
		Configuration.setDefaultApiClient(client);

		CoreV1Api api = new CoreV1Api();
		api.deleteNamespacedConfigMap(CONFIG_WATCHER_WIREMOCK_APP_NAME, NAMESPACE, null,
				null, null, null, null, null);
	}

	private void waitForDeployment(String deploymentName) {
		await().atMost(90, TimeUnit.SECONDS)
				.until(() -> isDeployentReady(deploymentName));
	}

	private boolean isDeployentReady(String deploymentName) throws ApiException {
		AppsV1Api appsApi = new AppsV1Api();
		V1DeploymentList deployments = appsApi.listNamespacedDeployment(NAMESPACE, null,
				null, "metadata.name=" + deploymentName, null, null, null, null, null);
		if (deployments.getItems().size() < 1) {
			fail();
		}
		V1Deployment deployment = deployments.getItems().get(0);
		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		System.out.println(
				"Available replicas for " + deploymentName + ": " + availableReplicas);
		return availableReplicas != null && availableReplicas >= 1;
	}

	private void deployConfigWatcher() throws ApiException {
		CoreV1Api api = new CoreV1Api();
		// Set the refresh interval to 0 so the refresh event gets sent immediately
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
				.withName(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME).endMetadata()
				.addToData("application.properties", new String(
						"spring.cloud.kubernetes.configuration.watcher.refreshDelay=0\n"
								+ "logging.level.org.springframework.cloud.kubernetes=TRACE"))
				.build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);

		Map<String, String> labels = new HashMap<>();
		labels.put("app", SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);
		int port = 8888;
		createDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, labels, labels,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, KIND_IMAGE_WITH_TAG,
				"IfNotPresent", port, port, "/actuator/health/readiness", port,
				"/actuator/health/liveness",
				SPRING_CLOUD_KUBERNETES_SERVICE_ACCOUNT_NAME);
		createService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, labels, labels,
				"ClusterIP", "http", port, port);
	}

	private void deployWiremock() throws ApiException {
		Map<String, String> labels = new HashMap<>();
		labels.put("app", CONFIG_WATCHER_WIREMOCK_APP_NAME);
		int port = 8080;
		V1Deployment wiremockDeployment = createDeployment(
				CONFIG_WATCHER_WIREMOCK_DEPLOYMENT_NAME, labels, labels,
				CONFIG_WATCHER_WIREMOCK_APP_NAME, "rodolpheche/wiremock", "IfNotPresent",
				port, port, "/__admin/mappings", port, "/__admin/mappings", "default");

		V1Service wiremockService = createService(CONFIG_WATCHER_WIREMOCK_APP_NAME,
				labels, labels, "ClusterIP", "http", port, port);

		NetworkingV1beta1Api networkingApi = new NetworkingV1beta1Api();
		NetworkingV1beta1Ingress ingress = new NetworkingV1beta1IngressBuilder()
				.editOrNewMetadata().withName("nginx-ingress")
				.addToAnnotations("nginx.ingress.kubernetes.io/rewrite-target", "/$2")
				.endMetadata().editOrNewSpec().addNewRule().editOrNewHttp().addNewPath()
				.withNewPath("/wiremock(/|$)(.*)").editOrNewBackend()
				.withNewServiceName(CONFIG_WATCHER_WIREMOCK_APP_NAME)
				.withNewServicePort(port).endBackend().endPath().endHttp().endRule()
				.endSpec().build();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);

	}

	private V1Service createService(String name, Map<String, String> labels,
			Map<String, String> specSelectors, String type, String portName, int port,
			int targetPort) throws ApiException {
		CoreV1Api api = new CoreV1Api();
		V1Service wiremockService = new V1ServiceBuilder().editOrNewMetadata()
				.withName(name).addToLabels(labels).endMetadata().editOrNewSpec()
				.addToSelector(specSelectors).withNewType(type).addNewPort()
				.withName(portName).withPort(port).withNewTargetPort(targetPort).endPort()
				.endSpec().build();
		return api.createNamespacedService(NAMESPACE, wiremockService, null, null, null);
	}

	private V1Deployment createDeployment(String name,
			Map<String, String> selectorMatchLabels,
			Map<String, String> templateMetadataLabels, String containerName,
			String image, String pullPolicy, int containerPort, int readinessProbePort,
			String readinessProbePath, int livenessProbePort, String livenessProbePath,
			String serviceAccountName) throws ApiException {
		AppsV1Api appsApi = new AppsV1Api();

		V1Deployment wiremockDeployment = new V1DeploymentBuilder().editOrNewMetadata()
				.withName(name).endMetadata().editOrNewSpec().withNewSelector()
				.addToMatchLabels(selectorMatchLabels).endSelector().editOrNewTemplate()
				.editOrNewMetadata().addToLabels(templateMetadataLabels).endMetadata()
				.editOrNewSpec().withServiceAccountName(serviceAccountName)
				.addNewContainer().withName(containerName).withImage(image)
				.withImagePullPolicy(pullPolicy).addNewPort()
				.withContainerPort(containerPort).endPort().editOrNewReadinessProbe()
				.editOrNewHttpGet().withNewPort(readinessProbePort)
				.withNewPath(readinessProbePath).endHttpGet().endReadinessProbe()
				.editOrNewLivenessProbe().editOrNewHttpGet()
				.withNewPort(livenessProbePort).withNewPath(livenessProbePath)
				.endHttpGet().endLivenessProbe().endContainer().endSpec().endTemplate()
				.endSpec().build();
		return appsApi.createNamespacedDeployment(NAMESPACE, wiremockDeployment, null,
				null, null);

	}

	private void waitForEndpointReady(String name) throws Exception {
		await().atMost(90, TimeUnit.SECONDS).until(() -> isEndpointReady(name));
	}

	private boolean isEndpointReady(String name) throws ApiException {
		CoreV1Api api = new CoreV1Api();
		V1EndpointsList endpoints = api.listNamespacedEndpoints(NAMESPACE, null, null,
				"metadata.name=" + name, null, null, null, null, null);
		if (endpoints.getItems().isEmpty()) {
			fail("no endpoints for " + name);
		}
		V1Endpoints endpoint = endpoints.getItems().get(0);
		return endpoint.getSubsets().get(0).getAddresses().size() >= 1;
	}

}
