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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigMapEventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-event-reload";

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
	void testInformFromOneNamespaceEventNotTriggered() {
		manifests("one", Phase.CREATE);

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
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange);

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
	void testInformFromOneNamespaceEventTriggered() {
		manifests("two", Phase.CREATE);

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange);

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
	void testInform() {
		manifests("three", Phase.CREATE);

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
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange);

		// sleep for 5 seconds
		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

		// nothing changes in our app, because we are watching only labeled configmaps
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", rightResult);

		// then deploy a new version of right-with-label-configmap
		ConfigMap rightWithLabelConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(
						new ObjectMetaBuilder().withNamespace("right").withName("right-configmap-with-label").build())
				.withData(Map.of("right.with.label.value", "right-with-label-after-change")).build();

		replaceConfigMap(rightWithLabelConfigMapAfterChange);

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

	private static void manifests(String activeProfile, Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream leftConfigMapStream = util.inputStream("left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("right-configmap.yaml");
		InputStream rightWithLabelConfigMapStream = util.inputStream("right-configmap-with-label.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();

		List<EnvVar> envVars = new ArrayList<>(
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar activeProfileProperty = new EnvVarBuilder().withName("SPRING_PROFILES_ACTIVE")
			.withValue(activeProfile).build();
		envVars.add(activeProfileProperty);

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();
		ConfigMap leftConfigMap = client.configMaps().load(leftConfigMapStream).get();
		ConfigMap rightConfigMap = client.configMaps().load(rightConfigMapStream).get();
		ConfigMap rightWithLabelConfigMap = client.configMaps().load(rightWithLabelConfigMapStream).get();

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait("left", leftConfigMap, null);
			util.createAndWait("right", rightConfigMap, null);
			if ("three".equals(activeProfile)) {
				util.createAndWait("right", rightWithLabelConfigMap, null);
			}
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait("left", leftConfigMap, null);
			util.deleteAndWait("right", rightConfigMap, null);
			if ("three".equals(activeProfile)) {
				util.deleteAndWait("right", rightWithLabelConfigMap, null);
			}
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	private static void replaceConfigMap(ConfigMap configMap) {
		client.configMaps().inNamespace("right").resource(configMap).createOrReplace();
	}

}
