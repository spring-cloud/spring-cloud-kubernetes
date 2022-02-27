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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
public class LoadBalancerIT {

	private static final Log LOG = LogFactory.getLog(LoadBalancerIT.class);

	private static final String WIREMOCK_DEPLOYMENT_NAME = "servicea-wiremock-deployment";

	private static final String WIREMOCK_APP_NAME = "servicea-wiremock";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-loadbalancer-it-deployment";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-client-loadbalancer-it";

	private static final String NAMESPACE = "default";

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

		deployWiremock();

		// Check to make sure the wiremock deployment is ready
		k8SUtils.waitForDeployment(WIREMOCK_DEPLOYMENT_NAME, NAMESPACE);

		// Check to see if endpoint is ready
		k8SUtils.waitForEndpointReady(WIREMOCK_APP_NAME, NAMESPACE);

	}

	@Test
	public void testLoadBalancerServiceMode() throws Exception {
		try {
			deployLoadbalancerServiceIt();
			testLoadBalancer();
		}
		finally {
			cleanup();
		}
	}

	@Test
	public void testLoadBalancerPodMode() throws Exception {
		try {
			deployLoadbalancerPodIt();
			testLoadBalancer();
		}
		finally {
			cleanup();
		}
	}

	private void cleanup() throws ApiException {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void testLoadBalancer() {
		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, NAMESPACE);
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
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).ignoreExceptions()
				.until(() -> rest.getForEntity("http://localhost:80/loadbalancer-it/servicea", String.class)
						.getStatusCode().is2xxSuccessful());
		Map<String, Object> result = rest.getForObject("http://localhost:80/loadbalancer-it/servicea", Map.class);
		assertThat(result.containsKey("mappings")).isTrue();
		assertThat(result.containsKey("meta")).isTrue();

	}

	@AfterEach
	public void after() throws Exception {
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null, null);

		api.deleteNamespacedService(WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("wiremock-ingress", NAMESPACE, null, null, null, null, null, null);

	}

	private void deployLoadbalancerServiceIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getLoadbalancerServiceItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getLoadbalancerItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getLoadbalancerItIngress(), null, null, null);
	}

	private void deployLoadbalancerPodIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getLoadbalancerPodItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getLoadbalancerItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getLoadbalancerItIngress(), null, null, null);
	}

	private V1Deployment getLoadbalancerServiceItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-service-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private V1Deployment getLoadbalancerPodItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-service-it-deployment.yaml");
		String image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + ":"
				+ getPomVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
		return deployment;
	}

	private void deployWiremock() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getWireockDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getWiremockAppService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getWiremockIngress(), null, null, null);
	}

	private V1Ingress getLoadbalancerItIngress() throws Exception {
		return (V1Ingress) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-ingress.yaml");
	}

	private V1Service getLoadbalancerItService() throws Exception {
		return (V1Service) K8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-service.yaml");
	}

	private V1Ingress getWiremockIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("wiremock-ingress.yaml");
	}

	private V1Service getWiremockAppService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("wiremock-service.yaml");
	}

	private V1Deployment getWireockDeployment() throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath("wiremock-deployment.yaml");
	}

}
