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

package org.springframework.cloud.kubernetes.fabric8.configmap.event.reload;

import java.time.Duration;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Assertions;
<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
=======
import org.testcontainers.containers.Container;
>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.builder;
<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.logs;
=======
>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.replaceConfigMap;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.retrySpec;

final class DataChangesInConfigMapReloadDelegate {

<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-reload";

	private static final String LEFT_NAMESPACE = "left";

=======
	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-event-reload";

	private static final String LEFT_NAMESPACE = "left";

	private static final K3sContainer K3S = Commons.container();

>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
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
<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
	static void testDataChangesInConfigMap(KubernetesClient client, K3sContainer container, String appLabelValue) {
=======
	static void testDataChangesInConfigMap(KubernetesClient client) {
>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		WebClient webClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then deploy a new version of left-configmap, but without changing its data,
		// only add a label
		ConfigMap configMap = new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder()
				.withLabels(Map.of("new-label", "abc")).withNamespace("left").withName("left-configmap").build())
				.withData(Map.of("left.value", "left-initial")).build();

		replaceConfigMap(client, configMap, "left");

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "left-initial".equals(innerResult);
		});

<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
		String logs = logs(container, appLabelValue);
=======
		String logs = logs();
>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
		Assertions.assertTrue(logs.contains("ConfigMap left-configmap was updated in namespace left"));
		Assertions.assertTrue(logs.contains("data in configmap has not changed, will not reload"));

		// change data
		configMap = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("new-label", "abc")).withNamespace("left")
						.withName("left-configmap").build())
				.withData(Map.of("left.value", "left-after-change")).build();

		replaceConfigMap(client, configMap, "left");

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "left-after-change".equals(innerResult);
		});

	}

<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
=======
	private static String logs() {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

>>>>>>> 3.0.x:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-configmap-event-reload/src/test/java/org/springframework/cloud/kubernetes/fabric8/configmap/event/reload/DataChangesInConfigMapReloadDelegate.java
}
