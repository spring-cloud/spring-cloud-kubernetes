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

import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;

/**
 * @author Ryan Baxter
 */
public class LoadBalancerIT {

	private static final String KIND_REPO_HOST_PORT = "localhost:5000";

	private static final String KIND_REPO_URL = "http://" + KIND_REPO_HOST_PORT;

	private static final String IMAGE = "spring-cloud-kubernetes-configuration-watcher";

	private static final String IMAGE_TAG = "2.0.0-SNAPSHOT";

	private static final String LOCAL_REPO = "docker.io/springcloud";

	private static final String LOCAL_IMAGE = LOCAL_REPO + "/" + IMAGE + ":" + IMAGE_TAG;

	private static final String KIND_IMAGE = KIND_REPO_HOST_PORT + "/" + IMAGE;

	private static final String KIND_IMAGE_WITH_TAG = KIND_IMAGE + ":" + IMAGE_TAG;

	private static final String WIREMOCK_DEPLOYMENT_NAME = "servicea-wiremock-deployment";

	private static final String WIREMOCK_APP_NAME = "servicea-wiremock";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-loadbalancer-it-deployment";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-client-loadbalancer-it";

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

		deployLoadbalancerIt();

		// Check to make sure the controller deployment is ready
		k8SUtils.waitForDeployment(SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, NAMESPACE);
	}

	@Test
	public void testLoadBalancer() {
		RestTemplate rest = new RestTemplateBuilder().build();
		Map<String, Object> result = rest.getForObject("http://localhost:80/loadbalancer-it/servicea", Map.class);
		assertThat(result.containsKey("mappings")).isTrue();
		assertThat(result.containsKey("meta")).isTrue();
	}

	@After
	public void after() throws Exception {

		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + SPRING_CLOUD_K8S_LOADBALANCER_DEPLOYMENT_NAME, null, null, null, null, null, null,
				null, null);
		appsApi.deleteCollectionNamespacedDeployment(NAMESPACE, null, null, null,
				"metadata.name=" + WIREMOCK_DEPLOYMENT_NAME, null, null, null, null, null, null, null, null);
		api.deleteNamespacedService(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, NAMESPACE, null, null, null, null, null,
				null);
		api.deleteNamespacedService(WIREMOCK_APP_NAME, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("wiremock-ingress", NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress("it-ingress", NAMESPACE, null, null, null, null, null, null);
	}

	private void deployLoadbalancerIt() throws Exception {
		appsApi.createNamespacedDeployment(NAMESPACE, getLoadbalancerItDeployment(), null, null, null);
		api.createNamespacedService(NAMESPACE, getLoadbalancerItService(), null, null, null);
		networkingApi.createNamespacedIngress(NAMESPACE, getLoadbalancerItIngress(), null, null, null);
	}

	private V1Service getLoadbalancerItService() throws Exception {
		V1Service service = (V1Service) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-service.yaml");
		return service;
	}

	private V1Deployment getLoadbalancerItDeployment() throws Exception {
		V1Deployment deployment = (V1Deployment) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-deployment.yaml");
		return deployment;
	}

	private NetworkingV1beta1Ingress getLoadbalancerItIngress() throws Exception {
		NetworkingV1beta1Ingress ingress = (NetworkingV1beta1Ingress) k8SUtils
				.readYamlFromClasspath("spring-cloud-kubernetes-client-loadbalancer-it-ingress.yaml");
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
