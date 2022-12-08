/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher.multiple.apps;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
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
 * @author wind57
 */
class ConfigurationWatcherMultipleAppIT {

	private static final String CONFIG_WATCHER_APP_A_IMAGE = "spring-cloud-kubernetes-client-configuration-watcher-secrets-app-a";

	private static final String CONFIG_WATCHER_APP_B_IMAGE = "spring-cloud-kubernetes-client-configuration-watcher-secrets-app-b";

	private static final String CONFIG_WATCHER_DEPLOYMENT_APP_A_NAME = "app-a-deployment";

	private static final String CONFIG_WATCHER_DEPLOYMENT_APP_B_NAME = "app-b-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String SECRET_NAME = "multiple-apps";

	private static final String NAMESPACE = "default";

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

		Commons.validateImage(CONFIG_WATCHER_APP_A_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_A_IMAGE, K3S);

		Commons.validateImage(CONFIG_WATCHER_APP_B_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_B_IMAGE, K3S);

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
		Commons.cleanUp(CONFIG_WATCHER_APP_A_IMAGE, K3S);
		Commons.cleanUp(CONFIG_WATCHER_APP_B_IMAGE, K3S);
	}

	@BeforeEach
	void setup() throws Exception {

		deployRabbitMq();
		deployAppA();
		deployAppB();
		deployIngress();
		deployConfigWatcher();

		k8SUtils.waitForReplicationController("rabbitmq-controller", NAMESPACE);
		waitForDeployment(CONFIG_WATCHER_DEPLOYMENT_APP_A_NAME);
		waitForDeployment(CONFIG_WATCHER_DEPLOYMENT_APP_B_NAME);
		waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME);
	}

	@AfterEach
	void after() throws Exception {

		cleanRabbitMq();
		cleanUpServices();
		cleanUpDeployments();
		cleanUpIngress();
		cleanUpConfigMaps();

		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(CONFIG_WATCHER_DEPLOYMENT_APP_A_NAME, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(CONFIG_WATCHER_DEPLOYMENT_APP_B_NAME, NAMESPACE);
	}

	@Test
	void testRefresh() throws Exception {

		// secret has one label, one that says that we should refresh
		// and one annotation that says that we should refresh some specific services
		V1Secret secret = new V1SecretBuilder().editOrNewMetadata().withName(SECRET_NAME)
				.addToLabels("spring.cloud.kubernetes.secret", "true")
				.addToAnnotations("spring.cloud.kubernetes.secret.apps",
						"spring-cloud-kubernetes-client-configuration-watcher-secret-app-a, "
								+ "spring-cloud-kubernetes-client-configuration-watcher-secret-app-b")
				.endMetadata().build();
		api.createNamespacedSecret(NAMESPACE, secret, null, null, null, null);

		WebClient.Builder builderA = builder();
		WebClient serviceClientA = builderA.baseUrl("http://localhost:80/app-a").build();

		WebClient.Builder builderB = builder();
		WebClient serviceClientB = builderB.baseUrl("http://localhost:80/app-b").build();

		Boolean[] valueA = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {
			valueA[0] = serviceClientA.method(HttpMethod.GET).retrieve().bodyToMono(Boolean.class)
					.retryWhen(retrySpec()).block();
			return valueA[0];
		});

		Assertions.assertThat(valueA[0]).isTrue();

		Boolean[] valueB = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {
			valueB[0] = serviceClientB.method(HttpMethod.GET).retrieve().bodyToMono(Boolean.class)
					.retryWhen(retrySpec()).block();
			return valueB[0];
		});

		Assertions.assertThat(valueB[0]).isTrue();
	}

	/**
	 * <pre>
	 --------------------------------------------------- rabbitmq -----------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 </pre>
	 */
	private void deployRabbitMq() throws Exception {
		api.createNamespacedService(NAMESPACE, getRabbitMqService(), null, null, null, null);
		String[] image = getRabbitMQReplicationController().getSpec().getTemplate().getSpec().getContainers().get(0)
				.getImage().split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "rabbitmq", K3S);
		api.createNamespacedReplicationController(NAMESPACE, getRabbitMQReplicationController(), null, null, null,
				null);
	}

	private V1ReplicationController getRabbitMQReplicationController() throws Exception {
		return (V1ReplicationController) K8SUtils.readYamlFromClasspath("rabbitmq/rabbitmq-controller.yaml");
	}

	private V1Service getRabbitMqService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("rabbitmq/rabbitmq-service.yaml");
	}

	/**
	 * <pre>
	 ----------------------------------------------------- app-a ------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 </pre>
	 */
	private void deployAppA() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getAppADeployment(), null, null, null, null);
		api.createNamespacedService(NAMESPACE, getAppAService(), null, null, null, null);
	}

	private V1Deployment getAppADeployment() throws Exception {
		String urlString = "app-a/app-a-deployment.yaml";
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(urlString);
		String image = K8SUtils.getImageFromDeployment(deployment) + ":" + getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getAppAService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("app-a/app-a-service.yaml");
	}

	/**
	 * <pre>
	 --------------------------------------------------- app-b --------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 </pre>
	 */
	private void deployAppB() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getAppBDeployment(), null, null, null, null);
		api.createNamespacedService(NAMESPACE, getAppBService(), null, null, null, null);
	}

	private V1Deployment getAppBDeployment() throws Exception {
		String urlString = "app-b/app-b-deployment.yaml";
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(urlString);
		String image = K8SUtils.getImageFromDeployment(deployment) + ":" + getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getAppBService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("app-b/app-b-service.yaml");
	}

	/**
	 * <pre>
	 ------------------------------------------------ config-watcher --------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 ------------------------------------------------------------------------------------------------------------
	 </pre>
	 */
	private void deployConfigWatcher() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null, null);
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(
				"config-watcher/spring-cloud-kubernetes-configuration-watcher-it-bus-amqp-deployment.yaml");
		String image = K8SUtils.getImageFromDeployment(deployment) + ":" + getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getConfigWatcherService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");
	}

	/**
	 * <pre>
		------------------------------------------------ common ----------------------------------------------------
		------------------------------------------------------------------------------------------------------------
		------------------------------------------------------------------------------------------------------------
		------------------------------------------------------------------------------------------------------------
	 </pre>
	 */

	private void deployIngress() throws Exception {
		V1Ingress ingress = (V1Ingress) K8SUtils.readYamlFromClasspath(
				"ingress/spring-cloud-kubernetes-configuration-watcher-multiple-apps-ingress.yaml");

		networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null, null);
		k8SUtils.waitForIngress(ingress.getMetadata().getName(), NAMESPACE);
	}

	private void waitForDeployment(String deploymentName) {
		await().pollInterval(Duration.ofSeconds(3)).atMost(600, TimeUnit.SECONDS)
				.until(() -> k8SUtils.isDeploymentReady(deploymentName, NAMESPACE));
	}

	private void cleanRabbitMq() throws Exception {
		api.deleteNamespacedService("rabbitmq-service", NAMESPACE, null, null, null, null, null, null);
		try {
			api.deleteNamespacedReplicationController("rabbitmq-controller", NAMESPACE, null, null, null, null, null,
					null);
		}
		catch (Exception e) {
			// swallowing this exception, delete does actually happen, it's a problem
			// downstream from the k8s client; see:
			// https://github.com/kubernetes-client/java/issues/86#issuecomment-411234259
		}
	}

	private void cleanUpServices() throws Exception {
		api.deleteNamespacedService("app-a", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService("app-b", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
	}

	private void cleanUpDeployments() throws Exception {
		appsApi.deleteNamespacedDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE, null, null, null,
				null, null, null);
		appsApi.deleteNamespacedDeployment(CONFIG_WATCHER_DEPLOYMENT_APP_A_NAME, NAMESPACE, null, null, null, null,
				null, null);

		appsApi.deleteNamespacedDeployment(CONFIG_WATCHER_DEPLOYMENT_APP_B_NAME, NAMESPACE, null, null, null, null,
				null, null);
	}

	private void cleanUpConfigMaps() throws Exception {
		api.deleteNamespacedSecret(SECRET_NAME, NAMESPACE, null, null, null, null, null, null);
	}

	private void cleanUpIngress() throws Exception {
		networkingApi.deleteNamespacedIngress("it-ingress-multiple-apps", NAMESPACE, null, null, null, null, null,
				null);
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(240, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
