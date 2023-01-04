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

package org.springframework.cloud.kubernetes.client.catalog;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;

/**
 * @author wind57
 */
class KubernetesClientCatalogWatchIT {

	private static final String APP_NAME = "spring-cloud-kubernetes-client-catalog-watcher";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static String busyboxServiceName;

	private static String busyboxDeploymentName;

	private static String appDeploymentName;

	private static String appServiceName;

	private static String appIngressName;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(APP_NAME, K3S);

		createApiClient(K3S.getKubeConfigYaml());
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);
		k8SUtils.setUp(NAMESPACE);
	}

	@BeforeEach
	void beforeEach() throws Exception {
		deployBusyboxManifests();
	}

	@AfterEach
	void afterEach() throws Exception {
		deleteApp();
	}

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service
	 *     - assert that we receive only spring-cloud-kubernetes-client-catalog-watcher pod
	 * </pre>
	 */
	@Test
	void testCatalogWatchWithEndpoints() throws Exception {
		deployApp(false);
		assertLogStatement("stateGenerator is of type: KubernetesEndpointsCatalogWatch");
		test();
	}

	@Test
	void testCatalogWatchWithEndpointSlices() throws Exception {
		deployApp(true);
		assertLogStatement("stateGenerator is of type: KubernetesEndpointSlicesCatalogWatch");
		test();
	}

	/**
	 * we log in debug mode the type of the StateGenerator we use, be that Endpoints or
	 * EndpointSlices. Here we make sure that in the test we actually use the correct
	 * type.
	 */
	private void assertLogStatement(String log) throws Exception {
		String appPodName = K3S.execInContainer("kubectl", "get", "pods", "-l",
				"app=spring-cloud-kubernetes-client-catalog-watcher", "-o=name", "--no-headers").getStdout();
		String allLogs = K3S.execInContainer("kubectl", "logs", appPodName.trim()).getStdout();
		Assertions.assertTrue(allLogs.contains(log));
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	@SuppressWarnings("unchecked")
	private void test() throws Exception {

		WebClient client = builder().baseUrl("localhost/result").build();
		EndpointNameAndNamespace[] holder = new EndpointNameAndNamespace[2];
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(List.class, EndpointNameAndNamespace.class);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we get 3 pods as input, but because they are sorted by name in the catalog
			// watcher implementation
			// we will get the first busybox instances here.
			if (result != null) {
				holder[0] = result.get(0);
				holder[1] = result.get(1);
				return true;
			}

			return false;
		});

		EndpointNameAndNamespace resultOne = holder[0];
		EndpointNameAndNamespace resultTwo = holder[1];

		Assertions.assertNotNull(resultOne);
		Assertions.assertNotNull(resultTwo);

		Assertions.assertTrue(resultOne.endpointName().contains("busybox"));
		Assertions.assertTrue(resultTwo.endpointName().contains("busybox"));
		Assertions.assertEquals("default", resultOne.namespace());
		Assertions.assertEquals("default", resultTwo.namespace());

		deleteBusyboxApp();

		// what we get after delete
		EndpointNameAndNamespace[] afterDelete = new EndpointNameAndNamespace[1];

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we need to get the event from KubernetesCatalogWatch, but that happens
			// on periodic bases. So in order to be sure we got the event we care about
			// we wait until the result has a single entry, which means busybox was
			// deleted
			// + KubernetesCatalogWatch received the new update.
			if (result != null && result.size() != 1) {
				return false;
			}

			// we will only receive one pod here, our own
			if (result != null) {
				afterDelete[0] = result.get(0);
				return true;
			}

			return false;
		});

		Assertions.assertTrue(afterDelete[0].endpointName().contains(APP_NAME));
		Assertions.assertEquals("default", afterDelete[0].namespace());

	}

	private void deployBusyboxManifests() throws Exception {

		V1Deployment busyboxDeployment = (V1Deployment) K8SUtils.readYamlFromClasspath(getBusyboxDeployment());

		String[] image = K8SUtils.getImageFromDeployment(busyboxDeployment).split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "busybox", K3S);

		appsApi.createNamespacedDeployment(NAMESPACE, busyboxDeployment, null, null, null, null);
		busyboxDeploymentName = busyboxDeployment.getMetadata().getName();

		V1Service busyboxService = (V1Service) K8SUtils.readYamlFromClasspath(getBusyboxService());
		busyboxServiceName = busyboxService.getMetadata().getName();
		api.createNamespacedService(NAMESPACE, busyboxService, null, null, null, null);

		k8SUtils.waitForDeployment(busyboxDeploymentName, NAMESPACE);

	}

	private static void deployApp(boolean useEndpointSlices) throws Exception {

		V1Deployment appDeployment = useEndpointSlices
				? (V1Deployment) K8SUtils.readYamlFromClasspath(getEndpointSlicesAppDeployment())
				: (V1Deployment) K8SUtils.readYamlFromClasspath(getEndpointsAppDeployment());

		String version = K8SUtils.getPomVersion();
		String currentImage = appDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		appDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);

		appsApi.createNamespacedDeployment(NAMESPACE, appDeployment, null, null, null, null);
		appDeploymentName = appDeployment.getMetadata().getName();

		V1Service appService = (V1Service) K8SUtils.readYamlFromClasspath(getAppService());
		appServiceName = appService.getMetadata().getName();
		api.createNamespacedService(NAMESPACE, appService, null, null, null, null);

		k8SUtils.waitForDeployment(appDeploymentName, NAMESPACE);

		V1Ingress appIngress = (V1Ingress) K8SUtils.readYamlFromClasspath(getAppIngress());
		appIngressName = appIngress.getMetadata().getName();
		networkingApi.createNamespacedIngress(NAMESPACE, appIngress, null, null, null, null);
		k8SUtils.waitForIngress(appIngressName, NAMESPACE);

	}

	private void deleteBusyboxApp() throws Exception {
		appsApi.deleteNamespacedDeployment(busyboxDeploymentName, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(busyboxServiceName, NAMESPACE, null, null, null, null, null, null);
		k8SUtils.waitForDeploymentToBeDeleted(busyboxDeploymentName, NAMESPACE);
	}

	private void deleteApp() throws Exception {
		appsApi.deleteNamespacedDeployment(appDeploymentName, NAMESPACE, null, null, null, null, null, null);
		api.deleteNamespacedService(appServiceName, NAMESPACE, null, null, null, null, null, null);
		networkingApi.deleteNamespacedIngress(appIngressName, NAMESPACE, null, null, null, null, null, null);
		k8SUtils.waitForDeploymentToBeDeleted(busyboxDeploymentName, NAMESPACE);
	}

	private static String getBusyboxService() {
		return "busybox/service.yaml";
	}

	private static String getBusyboxDeployment() {
		return "busybox/deployment.yaml";
	}

	/**
	 * deployment where support for endpoint slices is equal to false
	 */
	private static String getEndpointsAppDeployment() {
		return "app/watcher-endpoints-deployment.yaml";
	}

	private static String getEndpointSlicesAppDeployment() {
		return "app/watcher-endpoint-slices-deployment.yaml";
	}

	private static String getAppIngress() {
		return "app/watcher-ingress.yaml";
	}

	private static String getAppService() {
		return "app/watcher-service.yaml";
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
