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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.getPomVersion;

/**
 * @author Kris Iyer
 */
class ActuatorRefreshKafkaIT {

	private static final String CONFIG_WATCHER_IT_IMAGE = "spring-cloud-kubernetes-configuration-watcher-it";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-it-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String NAMESPACE = "default";

	private static final String KAFKA_BROKER = "kafka-broker";

	private static final String KAFKA_SERVICE = "kafka";

	private static final String ZOOKEEPER_SERVICE = "zookeeper";

	private static final String ZOOKEEPER_DEPLOYMENT = "zookeeper";

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);

		Commons.validateImage(CONFIG_WATCHER_IT_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_IT_IMAGE, K3S);

		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		networkingApi = new NetworkingV1Api();
		k8SUtils.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.cleanUp(CONFIG_WATCHER_IT_IMAGE, K3S);
	}

	@BeforeEach
	void setup() throws Exception {

		deployZookeeper();
		deployKafka();
		deployTestApp();
		deployConfigWatcher();

		// Check to make sure the controller deployment is ready
		waitForDeployment(ZOOKEEPER_DEPLOYMENT);
		waitForDeployment(KAFKA_BROKER);
		waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME);
		waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME);
	}

	@AfterEach
	void after() throws Exception {

		cleanUpKafka();
		cleanUpZookeeper();
		cleanUpServices();
		cleanUpDeployments();

		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);

		cleanUpConfigMaps();

		// Check to make sure the controller deployment is deleted
		k8SUtils.waitForDeploymentToBeDeleted(KAFKA_BROKER, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(ZOOKEEPER_DEPLOYMENT, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME, NAMESPACE);
	}

	// TODO figure out why this one fails on bus-starter-4.0.0-SNAPSHOT
	// @Disabled
	@Test
	void testRefresh() throws Exception {
		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName(CONFIG_WATCHER_IT_IMAGE)
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata().addToData("foo", "hello world")
				.build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/it").build();

		Boolean[] value = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(120)).until(() -> {
			value[0] = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(Boolean.class).retryWhen(retrySpec())
					.block();
			return value[0];
		});

		Assertions.assertThat(value[0]).isTrue();
	}

	private void waitForDeployment(String deploymentName) {
		await().pollInterval(Duration.ofSeconds(3)).atMost(600, TimeUnit.SECONDS)
				.until(() -> k8SUtils.isDeploymentReady(deploymentName, NAMESPACE));
	}

	private void deployTestApp() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getItAppService(), null, null, null);

		V1Ingress ingress = getItIngress();
		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private void deployConfigWatcher() throws Exception {
		api.createNamespacedConfigMap(NAMESPACE, getConfigWatcherConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null);
	}

	private void deployZookeeper() throws Exception {
		api.createNamespacedService(NAMESPACE, getZookeeperService(), null, null, null);
		V1Deployment deployment = getZookeeperDeployment();
		String[] image = K8SUtils.getImageFromDeployment(deployment).split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "zookeeper", K3S);
		appsApi.createNamespacedDeployment(NAMESPACE, deployment, null, null, null);
	}

	private void deployKafka() throws Exception {
		api.createNamespacedService(NAMESPACE, getKafkaService(), null, null, null);
		V1Deployment deployment = getKafkaDeployment();
		String[] image = K8SUtils.getImageFromDeployment(deployment).split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "kafka", K3S);
		appsApi.createNamespacedDeployment(NAMESPACE, getKafkaDeployment(), null, null, null);
	}

	private void cleanUpKafka() throws Exception {
		appsApi.deleteNamespacedDeployment(KAFKA_BROKER, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(KAFKA_SERVICE, NAMESPACE, null, null, null, null, null, null);
	}

	private void cleanUpZookeeper() throws Exception {
		appsApi.deleteNamespacedDeployment(ZOOKEEPER_DEPLOYMENT, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(ZOOKEEPER_SERVICE, NAMESPACE, null, null, null, null, null, null);
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(
				"app-watcher/spring-cloud-kubernetes-configuration-watcher-bus-kafka-deployment.yaml");
		String image = K8SUtils.getImageFromDeployment(deployment) + ":" + getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Deployment getItDeployment() throws Exception {
		String urlString = "app/spring-cloud-kubernetes-configuration-watcher-it-bus-kafka-deployment.yaml";
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(urlString);
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

	private V1Service getItAppService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("app/spring-cloud-kubernetes-configuration-watcher-it-service.yaml");
	}

	private V1Ingress getItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("app/spring-cloud-kubernetes-configuration-watcher-it-ingress.yaml");
	}

	private V1Deployment getKafkaDeployment() throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath("kafka/kafka-deployment.yaml");
	}

	private V1Service getKafkaService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("kafka/kafka-service.yaml");
	}

	private V1Deployment getZookeeperDeployment() throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath("zookeeper/zookeeper-deployment.yaml");
	}

	private V1Service getZookeeperService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("zookeeper/zookeeper-service.yaml");
	}

	private void cleanUpConfigMaps() throws Exception {
		api.deleteNamespacedConfigMap(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedConfigMap(CONFIG_WATCHER_IT_IMAGE, NAMESPACE, null, null, null, null, null, null);
	}

	private void cleanUpDeployments() throws Exception {
		appsApi.deleteNamespacedDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE, null, null, null,
				null, null, null);
		appsApi.deleteNamespacedDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME, NAMESPACE, null, null,
				null, null, null, null);
	}

	private void cleanUpServices() throws Exception {
		api.deleteNamespacedService(CONFIG_WATCHER_IT_IMAGE, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
