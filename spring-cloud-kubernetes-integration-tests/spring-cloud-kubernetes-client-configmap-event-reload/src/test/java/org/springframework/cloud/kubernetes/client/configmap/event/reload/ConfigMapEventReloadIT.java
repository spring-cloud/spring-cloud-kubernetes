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
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigMapEventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-configmap-event-reload";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api api;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		util = new Util(K3S);
		util.createNamespace("left");
		util.createNamespace("right");
		util.setUpClusterWide(NAMESPACE, Set.of("left", "right"));
		api = new CoreV1Api();
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.deleteNamespace("left");
		util.deleteNamespace("right");
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
		manifests("one", Phase.CREATE);
		Commons.assertReloadLogStatements("added configmap informer for namespace",
			"added secret informer for namespace", IMAGE_NAME);

		WebClient webClient = builder().baseUrl("localhost/left").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then read the value from the right-configmap
		webClient = builder().baseUrl("localhost/right").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", result);

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
		Assertions.assertEquals("left-initial", result);

		manifests("one", Phase.DELETE);
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
		manifests("two", Phase.CREATE);
		Commons.assertReloadLogStatements("added configmap informer for namespace",
			"added secret informer for namespace", IMAGE_NAME);

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", result);

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
		Assertions.assertEquals("right-after-change", resultAfterChange[0]);

		manifests("two", Phase.DELETE);
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
		manifests("three", Phase.CREATE);
		Commons.assertReloadLogStatements("added configmap informer for namespace",
			"added secret informer for namespace", IMAGE_NAME);

		// read the initial value from the right-configmap
		WebClient rightWebClient = builder().baseUrl("localhost/right").build();
		String rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", rightResult);

		// then read the initial value from the right-with-label-configmap
		WebClient rightWithLabelWebClient = builder().baseUrl("localhost/with-label").build();
		String rightWithLabelResult = rightWithLabelWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-with-label-initial", rightWithLabelResult);

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
		Assertions.assertEquals("right-initial", rightResult);

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
		Assertions.assertEquals("right-with-label-after-change", resultAfterChange[0]);

		// right-configmap now will see the new value also, but only because the other
		// configmap has triggered the restart
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-after-change", rightResult);

		manifests("three", Phase.DELETE);
	}

	private static void manifests(String deploymentRoot, Phase phase) {

		try {

			V1ConfigMap leftConfigMap = (V1ConfigMap) util.yaml("left-configmap.yaml");
			V1ConfigMap rightConfigMap = (V1ConfigMap) util.yaml("right-configmap.yaml");
			V1ConfigMap rightWithLabelConfigMap = (V1ConfigMap) util.yaml("right-configmap-with-label.yaml");

			V1Deployment deployment = (V1Deployment) util.yaml(deploymentRoot + "/deployment.yaml");
			V1Service service = (V1Service) util.yaml("service.yaml");
			V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");

			if (phase.equals(Phase.CREATE)) {
				util.createAndWait("left", leftConfigMap, null);
				util.createAndWait("right", rightConfigMap, null);

				if ("three".equals(deploymentRoot)) {
					util.createAndWait("right", rightWithLabelConfigMap, null);
				}
				util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
			}

			if (phase.equals(Phase.DELETE)) {
				util.deleteAndWait("left", leftConfigMap, null);
				util.deleteAndWait("right", rightConfigMap, null);
				if ("three".equals(deploymentRoot)) {
					util.deleteAndWait("right", rightWithLabelConfigMap, null);
				}
				util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private static void replaceConfigMap(V1ConfigMap configMap, String name) throws ApiException {
		api.replaceNamespacedConfigMap(name, "right", configMap, null, null, null, null);
	}

}
