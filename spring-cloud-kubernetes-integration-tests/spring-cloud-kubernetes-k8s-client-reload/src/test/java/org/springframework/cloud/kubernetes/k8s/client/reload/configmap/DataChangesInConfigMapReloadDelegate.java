/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.reload.configmap;

import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
final class DataChangesInConfigMapReloadDelegate {

	private static final String NAMESPACE = "default";

	private static final String LEFT_NAMESPACE = "left";

	/**
	 * <pre>
	 *     - configMap with no labels and data: left.value = left-initial exists in namespace left
	 *     - we assert that we can read it correctly first, by invoking localhost/left
	 *
	 *     - then we change the configmap by adding a label, this in turn does not
	 *       change the result of localhost/left, because the data has not changed.
	 *
	 *     - then we change data inside the config map, and we must see the updated value
	 * </pre>
	 */
	static void testSimple(String dockerImage, String deploymentName, K3sContainer k3sContainer) {

		K8sClientConfigMapReloadITUtil.patchFour(deploymentName, NAMESPACE, dockerImage);
		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", deploymentName);

		WebClient webClient = K8sClientConfigMapReloadITUtil.builder().baseUrl("http://localhost/" + LEFT_NAMESPACE)
				.build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(K8sClientConfigMapReloadITUtil.retrySpec()).block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then deploy a new version of left-configmap, but without changing its data,
		// only add a label
		V1ConfigMap configMap = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withLabels(Map.of("new-label", "abc")).withNamespace("left").withName("left-configmap").build())
				.withData(Map.of("left.value", "left-initial")).build();

		replaceConfigMap(configMap);

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = K8sClientConfigMapReloadITUtil.builder()
					.baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(K8sClientConfigMapReloadITUtil.retrySpec()).block();
			return "left-initial".equals(innerResult);
		});

		String logs = K8sClientConfigMapReloadITUtil.logs(deploymentName, k3sContainer);
		Assertions.assertTrue(logs.contains("ConfigMap left-configmap was updated in namespace left"));
		Assertions.assertTrue(logs.contains("data in configmap has not changed, will not reload"));

		// change data
		configMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("new-label", "abc")).withNamespace("left")
						.withName("left-configmap").build())
				.withData(Map.of("left.value", "left-after-change")).build();

		replaceConfigMap(configMap);

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = K8sClientConfigMapReloadITUtil.builder()
					.baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(K8sClientConfigMapReloadITUtil.retrySpec()).block();
			return "left-after-change".equals(innerResult);
		});

	}

	private static void replaceConfigMap(V1ConfigMap configMap) {
		try {
			new CoreV1Api().replaceNamespacedConfigMap("left-configmap", LEFT_NAMESPACE, configMap, null, null, null,
					null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

}
