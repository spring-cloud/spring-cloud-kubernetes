/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.reactive.discoveryclient.it;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
class ReactiveDiscoveryClientIT {

	private static final Log LOG = LogFactory.getLog(ReactiveDiscoveryClientIT.class);

	private static final String DISCOVERYSERVER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-discoveryserver-deployment";

	private static final String DISCOVERYSERVER_APP_NAME = "spring-cloud-kubernetes-discoveryserver";

	private static final String SPRING_CLOUD_K8S_DISCOVERYCLIENT_DEPLOYMENT_NAME = "spring-cloud-kubernetes-discoveryclient-it-deployment";

	private static final String SPRING_CLOUD_K8S_DISCOVERYCLIENT_APP_NAME = "spring-cloud-kubernetes-discoveryclient-it";

	private static final String NAMESPACE = "default";

	private static ApiClient client;

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	@BeforeAll
	public static void setup() throws Exception {
		client = createApiClient();
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);

		deployDiscoveryServer();

		// Check to make sure the discovery server deployment is ready
		k8SUtils.waitForDeployment(DISCOVERYSERVER_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(DISCOVERYSERVER_APP_NAME, NAMESPACE);

	}

	@Test
	public void testDiscoveryClient() throws Exception {
		try {
			deployDiscoveryIt();
			testLoadBalancer();
			testHealth();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			cleanup();
		}
	}

	private void cleanup() throws ApiException {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_DISCOVERYCLIENT_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_DISCOVERYCLIENT_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() throws Exception {
		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_DISCOVERYCLIENT_DEPLOYMENT_NAME, NAMESPACE);
		RestTemplate rest = createRestTemplate();
		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForEntity("http://localhost:80/discoveryclient-it/services", String.class)
						.getStatusCode().is2xxSuccessful());
		String[] result = rest.getForObject("http://localhost:80/discoveryclient-it/services", String[].class);
		LOG.info("Services: " + result);
		assertThat(Arrays.stream(result).anyMatch(s -> "spring-cloud-kubernetes-discoveryserver".equalsIgnoreCase(s)))
				.isTrue();

	}

	private RestTemplate createRestTemplate() {
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
		return rest;
	}

	public void testHealth() {
		RestTemplate rest = createRestTemplate();

		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForEntity("http://localhost:80/discoveryclient-it/actuator/health", String.class)
						.getStatusCode().is2xxSuccessful());

		Map<String, Object> health = rest.getForObject("http://localhost:80/discoveryclient-it/actuator/health",
				Map.class);
		Map<String, Object> components = (Map) health.get("components");

		Map<String, Object> discoveryComposite = (Map) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");
	}

	@AfterAll
	public static void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + DISCOVERYSERVER_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null,
				null);

		api.deleteNamespacedService(DISCOVERYSERVER_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("discoveryserver-ingress", NAMESPACE, null, null, null, null, null, null);

	}

	private void deployDiscoveryIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getDiscoveryItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getDiscoveryService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getDiscoveryItIngress(), null, null, null);
	}

	private V1Service getDiscoveryService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryclient-it-service.yaml");
		return service;
	}

	private V1Deployment getDiscoveryItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryclient-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Ingress getDiscoveryItIngress() throws Exception {
		V1Ingress ingress = (V1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryclient-it-ingress.yaml");
		return ingress;
	}

	private static void deployDiscoveryServer() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getDiscoveryServerDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getDiscoveryServerService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getDiscoveryServerIngress(), null, null, null);
	}

	private static V1Ingress getDiscoveryServerIngress() throws Exception {
		V1Ingress ingress = (V1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryserver-ingress.yaml");
		return ingress;
	}

	private static V1Service getDiscoveryServerService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryserver-service.yaml");
		return service;
	}

	private static V1Deployment getDiscoveryServerDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-discoveryserver-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

}
