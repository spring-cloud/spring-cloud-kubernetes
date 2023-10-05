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

package org.springframework.cloud.kubernetes.fabric8.configmap.event.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.patchOne;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.patchThree;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.patchTwo;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.reCreateConfigMaps;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.replaceConfigMap;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.retrySpec;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;

/**
 * @author wind57
 */
class ConfigMapEventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-event-reload";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + pomVersion();

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static KubernetesClient client;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();

		util.createNamespace("left");
		util.createNamespace("right");
		util.setUpClusterWide(NAMESPACE, Set.of("left", "right"));

		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.deleteNamespace("left");
		util.deleteNamespace("right");
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();

		manifests(Phase.DELETE);
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
	void testInformFromOneNamespaceEventNotTriggered() {
		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		WebClient webClient = builder().baseUrl("http://localhost/left").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then read the value from the right-configmap
		webClient = builder().baseUrl("http://localhost/right").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(client, rightConfigMapAfterChange, "right");

		webClient = builder().baseUrl("http://localhost/left").build();

		WebClient finalWebClient = webClient;
		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(30)).until(() -> {
			String innerResult = finalWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			// left configmap has not changed, no restart of app has happened
			return "left-initial".equals(innerResult);
		});

		testInformFromOneNamespaceEventTriggered();
		testInform();
		testInformFromOneNamespaceEventTriggeredSecretsDisabled();
	}

	/**
	 * <pre>
	 * - there are two namespaces : left and right
	 * - each of the namespaces has one configmap
	 * - we watch the "right" namespace and make a change in the configmap in the same
	 * namespace
	 * - as such, event is triggered and we see the updated value
	 * </pre>
	 */
	void testInformFromOneNamespaceEventTriggered() {

		reCreateConfigMaps(util, client);
		patchOne(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);

		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("http://localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(client, rightConfigMapAfterChange, "right");

		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/right").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		Assertions.assertEquals("right-after-change", resultAfterChange[0]);
	}

	/**
	 * <pre>
	 * - there are two namespaces : left and right (though we do not care about the left
	 * one)
	 * - left has one configmap : left-configmap
	 * - right has two configmaps: right-configmap, right-configmap-with-label
	 * - we watch the "right" namespace, but enable tagging; which means that only
	 * right-configmap-with-label triggers changes.
	 * </pre>
	 */
	void testInform() {

		reCreateConfigMaps(util, client);
		patchTwo(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);

		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		// read the initial value from the right-configmap
		WebClient rightWebClient = builder().baseUrl("http://localhost/right").build();
		String rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", rightResult);

		// then read the initial value from the right-with-label-configmap
		WebClient rightWithLabelWebClient = builder().baseUrl("http://localhost/with-label").build();
		String rightWithLabelResult = rightWithLabelWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-with-label-initial", rightWithLabelResult);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(client, rightConfigMapAfterChange, "right");

		// nothing changes in our app, because we are watching only labeled configmaps
		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(30)).until(() -> {
			String innerRightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "right-initial".equals(innerRightResult);
		});

		// then deploy a new version of right-with-label-configmap
		ConfigMap rightWithLabelConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(
						new ObjectMetaBuilder().withNamespace("right").withName("right-configmap-with-label").build())
				.withData(Map.of("right.with.label.value", "right-with-label-after-change")).build();

		replaceConfigMap(client, rightWithLabelConfigMapAfterChange, "right");

		// since we have changed a labeled configmap, app will restart and pick up the new
		// value
		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/with-label").build();
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
	}

	/**
	 * <pre>
	 * - there are two namespaces : left and right
	 * - each of the namespaces has one configmap
	 * - secrets are disabled
	 * - we watch the "right" namespace and make a change in the configmap in the same
	 * namespace
	 * - as such, event is triggered and we see the updated value
	 * </pre>
	 */
	void testInformFromOneNamespaceEventTriggeredSecretsDisabled() {

		reCreateConfigMaps(util, client);
		patchThree(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);

		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("http://localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(client, rightConfigMapAfterChange, "right");

		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/right").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		Assertions.assertEquals("right-after-change", resultAfterChange[0]);

	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream leftConfigMapStream = util.inputStream("left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("right-configmap.yaml");
		InputStream rightWithLabelConfigMapStream = util.inputStream("right-configmap-with-label.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);
		ConfigMap leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		ConfigMap rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);
		ConfigMap rightWithLabelConfigMap = Serialization.unmarshal(rightWithLabelConfigMapStream, ConfigMap.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait("left", leftConfigMap, null);
			util.createAndWait("right", rightConfigMap, null);
			util.createAndWait("right", rightWithLabelConfigMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait("left", leftConfigMap, null);
			util.deleteAndWait("right", rightConfigMap, null);
			util.deleteAndWait("right", rightWithLabelConfigMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

}
