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

package org.springframework.cloud.kubernetes.client.reactive.discovery.it;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
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
public class ReactiveDiscoveryClientIT {

	private static final Log LOG = LogFactory.getLog(ReactiveDiscoveryClientIT.class);

	private static final String WIREMOCK_DEPLOYMENT_NAME = "servicea-wiremock-deployment";

	private static final String WIREMOCK_APP_NAME = "servicea-wiremock";

	private static final String SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-reactive-discovery-it-deployment";

	private static final String SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME = "spring-cloud-kubernetes-client-reactive-discovery-it";

	private static final String NAMESPACE = "default";

	private ApiClient client;

	private CoreV1Api api;

	private AppsV1Api appsApi;

	private NetworkingV1beta1Api networkingApi;

	private K8SUtils k8SUtils;

	@Before
	public void setup() throws Exception {
		this.client = createApiClient();
		this.api = new CoreV1Api();
		this.appsApi = new AppsV1Api();
		this.networkingApi = new NetworkingV1beta1Api();
		this.k8SUtils = new K8SUtils(api, appsApi);

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(WIREMOCK_APP_NAME, NAMESPACE);

	}

	@Test
	public void testReactiveDiscoveryClient() throws Exception {
		try {
			deployReactiveDiscoveryIt();
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
				"metadata.name=" + SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME, null, null, null, null, null,
				null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_APP_NAME, NAMESPACE, null, null, null, null,
				null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() throws Exception {
		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_REACTIVE_DISCOVERY_DEPLOYMENT_NAME, NAMESPACE);
		RestTemplate rest = createRestTemplate();
		// Sometimes the NGINX ingress takes a bit to catch up and realize the service is
		// available and we get a 503, we just need to wait a bit
		await().timeout(Duration.ofSeconds(60))
				.until(() -> rest.getForEntity("http://localhost:80/reactive-discovery-it/services", String.class)
						.getStatusCode().is2xxSuccessful());
		String result = rest.getForObject("http://localhost:80/reactive-discovery-it/services", String.class);
		assertThat(Arrays.stream(result.split(",")).anyMatch(s -> "servicea-wiremock".equalsIgnoreCase(s))).isTrue();

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
				.until(() -> rest
						.getForEntity("http://localhost:80/reactive-discovery-it/actuator/health", String.class)
						.getStatusCode().is2xxSuccessful());

		Map<String, Object> health = rest.getForObject("http://localhost:80/reactive-discovery-it/actuator/health",
				Map.class);
		Map<String, Object> components = (Map) health.get("components");

		assertThat(components.containsKey("reactiveDiscoveryClients")).isTrue();
		Map<String, Object> discoveryComposite = (Map) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");
	}

	@After
	public void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null, null);

		api.deleteNamespacedService(WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("wiremock-ingress", NAMESPACE, null, null, null, null, null, null);

	}

	private void deployReactiveDiscoveryIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getReactiveDiscoveryItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getReactiveDiscoveryService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getReactiveDiscoveryItIngress(), null, null, null);
	}

	private V1Service getReactiveDiscoveryService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-service.yaml");
		return service;
	}

	private V1Deployment getReactiveDiscoveryItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private NetworkingV1beta1Ingress getReactiveDiscoveryItIngress() throws Exception {
		NetworkingV1beta1Ingress ingress = (NetworkingV1beta1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-reactive-discovery-it-ingress.yaml");
		return ingress;
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
