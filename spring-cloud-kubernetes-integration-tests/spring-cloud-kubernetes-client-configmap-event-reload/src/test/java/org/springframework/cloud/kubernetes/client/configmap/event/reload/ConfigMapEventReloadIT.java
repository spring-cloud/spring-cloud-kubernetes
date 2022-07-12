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

package org.springframework.cloud.kubernetes.client.configmap.event.reload;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.processExecResult;
import static org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils.createApiClient;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigMapEventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-configmap-event-reload";

	private static final String NAMESPACE = "default";

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String leftConfigMapName;

	private static String rightConfigMapName;

	private static String rightWithLabelConfigMapName;

	private static CoreV1Api api;

	private static AppsV1Api appsApi;

	private static NetworkingV1Api networkingApi;

	private static K8SUtils k8SUtils;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		createApiClient(K3S.getKubeConfigYaml());
		createNamespaces();
		api = new CoreV1Api();
		appsApi = new AppsV1Api();
		networkingApi = new NetworkingV1Api();
		k8SUtils = new K8SUtils(api, appsApi);

		k8SUtils.setUpClusterWide(NAMESPACE, Set.of("left", "right"));
	}

	@AfterAll
	static void afterAll() throws Exception {
		deleteNamespaces();
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right
	 *     - each of the namespaces has one configmap
	 *     - we watch the "left" namespace, but make a change in the configmap in the right namespace
	 *     - as such, no event is triggered and "left-configmap" stays as-is
	 * </pre>
	 */
	@Test
	void testInformFromOneNamespaceEventNotTriggered() throws Exception {
		deployManifests("one");

		WebClient webClient = builder().baseUrl("localhost/left").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		assertThat("left-initial").isEqualTo(result);

		// then read the value from the right-configmap
		webClient = builder().baseUrl("localhost/right").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		assertThat("right-initial").isEqualTo(result);

		// then deploy a new version of right-configmap
		V1ConfigMap rightConfigMapAfterChange = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMeta().namespace("right").name("right-configmap"))
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		// wait dummy for 5 seconds
		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

		webClient = builder().baseUrl("localhost/left").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		// left configmap has not changed, no restart of app has happened
		assertThat("left-initial").isEqualTo(result);

		deleteManifests();
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right
	 *     - each of the namespaces has one configmap
	 *     - we watch the "right" namespace and make a change in the configmap in the same namespace
	 *     - as such, event is triggered and we see the updated value
	 * </pre>
	 */
	@Test
	void testInformFromOneNamespaceEventTriggered() throws Exception {
		deployManifests("two");

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		assertThat("right-initial").isEqualTo(result);

		// then deploy a new version of right-configmap
		V1ConfigMap rightConfigMapAfterChange = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMeta().namespace("right").name("right-configmap"))
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("localhost/right").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();

			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		assertThat("right-after-change").isEqualTo(resultAfterChange[0]);

		deleteManifests();
	}

	/**
	 * <pre>
	*     - there are two namespaces : left and right (though we do not care about the left one)
	*     - left has one configmap : left-configmap
	*     - right has two configmaps: right-configmap, right-configmap-with-label
	*     - we watch the "right" namespace, but enable tagging; which means that only
	*       right-configmap-with-label triggers changes.
	* </pre>
	 */
	@Test
	void testInform() throws Exception {
		deployManifests("three");

		// read the initial value from the right-configmap
		WebClient rightWebClient = builder().baseUrl("localhost/right").build();
		String rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		assertThat("right-initial").isEqualTo(rightResult);

		// then read the initial value from the right-with-label-configmap
		WebClient rightWithLabelWebClient = builder().baseUrl("localhost/with-label").build();
		String rightWithLabelResult = rightWithLabelWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		assertThat("right-with-label-initial").isEqualTo(rightWithLabelResult);

		// then deploy a new version of right-configmap
		V1ConfigMap rightConfigMapAfterChange = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMeta().namespace("right").name("right-configmap"))
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		// sleep for 5 seconds
		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

		// nothing changes in our app, because we are watching only labeled configmaps
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		assertThat("right-initial").isEqualTo(rightResult);

		// then deploy a new version of right-with-label-configmap
		V1ConfigMap rightWithLabelConfigMapAfterChange = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMeta().namespace("right").name("right-configmap-with-label"))
				.withData(Map.of("right.with.label.value", "right-with-label-after-change")).build();

		replaceConfigMap(rightWithLabelConfigMapAfterChange, "right-configmap-with-label");

		// since we have changed a labeled configmap, app will restart and pick up the new
		// value
		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("localhost/with-label").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		assertThat("right-with-label-after-change").isEqualTo(resultAfterChange[0]);

		// right-configmap now will see the new value also, but only because the other
		// configmap has triggered the restart
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		assertThat("right-after-change").isEqualTo(rightResult);

		deleteManifests();
	}

	private static void createNamespaces() throws Exception {
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace left"));
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace right"));
	}

	private static void deleteNamespaces() throws Exception {
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl delete namespace left"));
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl delete namespace right"));
	}

	private static void deployManifests(String deploymentRoot) {

		try {

			V1ConfigMap leftConfigMap = leftConfigMap();
			leftConfigMapName = leftConfigMap.getMetadata().getName();
			api.createNamespacedConfigMap("left", leftConfigMap, null, null, null);

			V1ConfigMap rightConfigMap = rightConfigMap();
			rightConfigMapName = rightConfigMap.getMetadata().getName();
			api.createNamespacedConfigMap("right", rightConfigMap, null, null, null);

			if ("three".equals(deploymentRoot)) {
				V1ConfigMap rightWithLabelConfigMap = rightWithLabelConfigMap();
				rightWithLabelConfigMapName = rightWithLabelConfigMap.getMetadata().getName();
				api.createNamespacedConfigMap("right", rightWithLabelConfigMap, null, null, null);
			}

			V1Deployment deployment = getDeployment(deploymentRoot);
			String version = K8SUtils.getPomVersion();
			String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);
			deploymentName = deployment.getMetadata().getName();
			appsApi.createNamespacedDeployment(NAMESPACE, deployment, null, null, null);

			V1Service service = getService();
			serviceName = service.getMetadata().getName();
			api.createNamespacedService(NAMESPACE, service, null, null, null);

			V1Ingress ingress = getIngress();
			ingressName = ingress.getMetadata().getName();
			networkingApi.createNamespacedIngress(NAMESPACE, ingress, null, null, null);

			k8SUtils.waitForIngress(ingressName, NAMESPACE);
			k8SUtils.waitForDeployment("spring-cloud-kubernetes-client-configmap-deployment-event-reload", NAMESPACE);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deleteManifests() {

		try {

			api.deleteNamespacedConfigMap(leftConfigMapName, "left", null, null, null, null, null, null);
			api.deleteNamespacedConfigMap(rightConfigMapName, "right", null, null, null, null, null, null);

			if (rightWithLabelConfigMapName != null) {
				api.deleteNamespacedConfigMap(rightWithLabelConfigMapName, "right", null, null, null, null, null, null);
			}

			appsApi.deleteNamespacedDeployment(deploymentName, NAMESPACE, null, null, null, null, null, null);
			api.deleteNamespacedService(serviceName, NAMESPACE, null, null, null, null, null, null);
			networkingApi.deleteNamespacedIngress(ingressName, NAMESPACE, null, null, null, null, null, null);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static V1ConfigMap leftConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils.readYamlFromClasspath("left-configmap.yaml");
	}

	private static V1ConfigMap rightConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils.readYamlFromClasspath("right-configmap.yaml");
	}

	private static V1ConfigMap rightWithLabelConfigMap() throws Exception {
		return (V1ConfigMap) K8SUtils.readYamlFromClasspath("right-configmap-with-label.yaml");
	}

	private static V1Deployment getDeployment(String root) throws Exception {
		return (V1Deployment) K8SUtils.readYamlFromClasspath(root + "/deployment.yaml");
	}

	private static V1Service getService() throws Exception {
		return (V1Service) K8SUtils.readYamlFromClasspath("service.yaml");
	}

	private static V1Ingress getIngress() throws Exception {
		return (V1Ingress) K8SUtils.readYamlFromClasspath("ingress.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	@SuppressWarnings({ "unchecked", "raw" })
	private static void replaceConfigMap(V1ConfigMap configMap, String name) throws ApiException {
		api.replaceNamespacedConfigMap(name, "right", configMap, null, null, null);
	}

}
