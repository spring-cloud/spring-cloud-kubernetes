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

import java.io.IOException;
import java.time.Duration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
public class ActuatorRefreshRabbitMQIT {

	private static final Log LOG = LogFactory.getLog(ActuatorRefreshRabbitMQIT.class);

	private Log log = LogFactory.getLog(getClass());

	private static final String CONFIG_WATCHER_IT_IMAGE = "spring-cloud-kubernetes-configuration-watcher-it";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-configuration-watcher-it-deployment";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String NAMESPACE = "default";

	private static final String RABBIT_MQ_CONTROLLER_NAME = "rabbitmq-controller";

	private ApiClient client;

	private CoreV1Api api;

	private AppsV1Api appsApi;

	private NetworkingV1Api networkingApi;

	private K8SUtils k8SUtils;

	@BeforeEach
	public void setup() throws Exception {
		this.client = createApiClient();
		this.api = new CoreV1Api();
		this.appsApi = new AppsV1Api();
		this.networkingApi = new NetworkingV1Api();
		this.k8SUtils = new K8SUtils(api, appsApi);

		deployRabbitMQ();
		deployTestApp();
		deployConfigWatcher();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForReplicationController(RABBIT_MQ_CONTROLLER_NAME, NAMESPACE);
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME, NAMESPACE);
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
	}

	@Test
	public void testRefresh() throws Exception {
		// Create new configmap to trigger controller to signal app to refresh
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName(CONFIG_WATCHER_IT_IMAGE)
				.addToLabels("spring.cloud.kubernetes.config", "true").endMetadata().addToData("foo", "hello world")
				.build();
		api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);
		RestTemplate rest = new RestTemplateBuilder().build();
		rest.setErrorHandler(new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse clientHttpResponse) throws IOException {
				LOG.warn("Received response status code: " + clientHttpResponse.getRawStatusCode());
				return clientHttpResponse.getRawStatusCode() != 503;
			}

			@Override
			public void handleError(ClientHttpResponse clientHttpResponse) {

			}
		});

		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60)).until(
				() -> rest.getForEntity("http://localhost:80/it", String.class).getStatusCode().is2xxSuccessful());

		// Wait a bit before we verify
		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(120)).until(() -> {
			Boolean value = rest.getForObject("http://localhost:80/it", Boolean.class);
			log.info("Returned " + value + " from http://localhost:80/it");
			return value;
		});

		assertThat(rest.getForObject("http://localhost:80/it", Boolean.class)).isTrue();
	}

	@AfterEach
	public void after() throws Exception {
		api.deleteNamespacedService("rabbitmq-service", NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(CONFIG_WATCHER_IT_IMAGE, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);

		appsApi.deleteNamespacedDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE, null, null, null,
				null, null, null);
		appsApi.deleteNamespacedDeployment(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME, NAMESPACE, null, null,
				null, null, null, null);

		try {
			api.deleteNamespacedReplicationController(RABBIT_MQ_CONTROLLER_NAME, NAMESPACE, null, null, null, null,
					null, null);
		}
		catch (Exception e) {
			// swallowing this exception, the delete does actually happen, its a problem
			// downstream from the k8s
			// client
			// see
			// https://github.com/kubernetes-client/java/issues/86#issuecomment-411234259
		}

		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);

		api.deleteNamespacedConfigMap(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedConfigMap(CONFIG_WATCHER_IT_IMAGE, NAMESPACE, null, null, null, null, null, null);

		// Check to make sure the controller deployment is deleted
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_DEPLOYMENT_NAME, NAMESPACE);
		k8SUtils.waitForDeploymentToBeDeleted(SPRING_CLOUD_K8S_CONFIG_WATCHER_IT_DEPLOYMENT_NAME, NAMESPACE);

	}

	private void deployTestApp() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getItAppService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getItIngress(), null, null, null);
	}

	private void deployConfigWatcher() throws Exception {
		api.createNamespacedConfigMap(NAMESPACE, getConfigWatcherConfigMap(), null, null, null);
		appsApi.createNamespacedDeployment(NAMESPACE, getConfigWatcherDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getConfigWatcherService(), null, null, null);
	}

	private void deployRabbitMQ() throws Exception {
		api.createNamespacedService(NAMESPACE, getRabbitMQService(), null, null, null);
		api.createNamespacedReplicationController(NAMESPACE, getRabbitMQReplicationController(), null, null, null);
	}

	private V1Deployment getConfigWatcherDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-bus-amqp-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Deployment getItDeployment() throws Exception {
		String urlString = "spring-cloud-kubernetes-configuration-watcher-it-bus-amqp-deployment.yaml";
		V1Deployment deployment = (V1Deployment) K8SUtils.readYamlFromClasspath(urlString);
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Service getItAppService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-it-service.yaml");
	}

	private V1Service getConfigWatcherService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-service.yaml");
	}

	private V1ConfigMap getConfigWatcherConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
	}

	private V1Ingress getItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-configuration-watcher-it-ingress.yaml");
	}

	private V1ReplicationController getRabbitMQReplicationController() throws Exception {
		return (V1ReplicationController) K8SUtils.readYamlFromClasspath("rabbitmq-controller.yaml");
	}

	private V1Service getRabbitMQService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("rabbitmq-service.yaml");
	}

}
