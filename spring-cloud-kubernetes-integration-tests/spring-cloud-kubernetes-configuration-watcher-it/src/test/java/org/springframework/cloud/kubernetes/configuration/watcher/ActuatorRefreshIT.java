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
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
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

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private String configWatcherConfigMapName;

	private static final String WIREMOCK_HOST = "localhost";

	private static final String WIREMOCK_PATH = "/";

	private static final int WIREMOCK_PORT = 80;

	private static final String NAMESPACE = "default";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		k8SUtils.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		k8SUtils.removeWiremockImage();
	}

	@BeforeEach
	void setup() throws Exception {
		deployConfigWatcher();
		k8SUtils.deployWiremock(NAMESPACE, true, K3S);
	}

	@AfterEach
	void after() throws Exception {

		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);

		api.deleteNamespacedConfigMap(configWatcherConfigMapName, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedConfigMap("servicea-wiremock", NAMESPACE, null, null, null, null, null, null);
		k8SUtils.cleanUpWiremock(NAMESPACE);
	}

	/*
	 * this test loads uses two services: wiremock on port 8080 and configuration-watcher
	 * on port 8888. we deploy configuration-watcher first and configure it via a
	 * configmap with the same name. then, we mock the call to actuator/refresh endpoint
	 * and deploy a new configmap: "servicea-wiremock", this in turn will trigger that
	 * refresh that we capture and assert for.
	 */
	@Test
	void testActuatorRefresh() throws Exception {
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT, WIREMOCK_PATH);
		await().timeout(Duration.ofSeconds(60)).ignoreException(VerificationException.class)
				.until(() -> stubFor(post(urlEqualTo("/actuator/refresh")).willReturn(aResponse().withStatus(200)))
						.getResponse().wasConfigured());

		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName("servicea-wiremock")
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata().addToData("foo", "bar").build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null, null);

		// Wait a bit before we verify
		await().atMost(Duration.ofSeconds(30))
				.until(() -> !findAll(postRequestedFor(urlEqualTo("/actuator/refresh"))).isEmpty());

		verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	private void deployConfigWatcher() throws Exception {
		V1ConfigMap configMap = getConfigWatcherConfigMap();
		configWatcherConfigMapName = configMap.getMetadata().getName();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null, null);

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(
				"config-watcher/spring-cloud-kubernetes-configuration-watcher-http-deployment.yaml");
		String image = K8SUtils.getImageFromDeployment(deployment) + ":" + getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getConfigWatcherService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");
	}

	private V1ConfigMap getConfigWatcherConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils
				.readYamlFromClasspath("config-watcher/spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
	}

}
