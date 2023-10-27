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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.awaitility.Awaitility.await;

class ActuatorRefreshMultipleNamespacesIT {

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String WIREMOCK_HOST = "localhost";

	private static final String WIREMOCK_PATH = "/";

	private static final int WIREMOCK_PORT = 80;

	private static final String DEFAULT_NAMESPACE = "default";

	private static final String LEFT_NAMESPACE = "left";

	private static final String RIGHT_NAMESPACE = "right";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		util = new Util(K3S);
		util.createNamespace(LEFT_NAMESPACE);
		util.createNamespace(RIGHT_NAMESPACE);
		util.wiremock(DEFAULT_NAMESPACE, "/", Phase.CREATE);
		util.setUpClusterWide(DEFAULT_NAMESPACE, Set.of(DEFAULT_NAMESPACE, LEFT_NAMESPACE, RIGHT_NAMESPACE));
		configWatcher(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		configWatcher(Phase.DELETE);
		util.wiremock(DEFAULT_NAMESPACE, "/", Phase.DELETE);
		util.deleteClusterWide(DEFAULT_NAMESPACE, Set.of(DEFAULT_NAMESPACE, LEFT_NAMESPACE, RIGHT_NAMESPACE));
		util.deleteNamespace(LEFT_NAMESPACE);
		util.deleteNamespace(RIGHT_NAMESPACE);
		Commons.cleanUp(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.systemPrune();
	}

	/**
	 * <pre>
	 *     - deploy config-watcher in default namespace
	 *     - deploy wiremock in default namespace (so that we could assert calls to the actuator path)
	 *     - deploy configmap-left in left namespaces with proper label and "service-wiremock" name. Because of the
	 *       label, this will trigger a reload; because of the name this will trigger a reload against that name.
	 *       This is a http refresh against the actuator.
	 *     - same as above for the configmap-right.
	 * </pre>
	 */
	@Test
	void testConfigMapActuatorRefreshMultipleNamespaces() {
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT, WIREMOCK_PATH);
		await().timeout(Duration.ofSeconds(60))
				.until(() -> WireMock
						.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
								.willReturn(WireMock.aResponse().withBody("{}").withStatus(200)))
						.getResponse().wasConfigured());

		// left-config-map
		V1ConfigMap leftConfigMap = new V1ConfigMapBuilder().editOrNewMetadata()
				.withLabels(Map.of("spring.cloud.kubernetes.config", "true")).withName("service-wiremock")
				.withNamespace(LEFT_NAMESPACE).endMetadata().addToData("color", "purple").build();
		util.createAndWait(LEFT_NAMESPACE, leftConfigMap, null);

		// right-config-map
		V1ConfigMap rightConfigMap = new V1ConfigMapBuilder().editOrNewMetadata()
				.withLabels(Map.of("spring.cloud.kubernetes.config", "true")).withName("service-wiremock")
				.withNamespace(RIGHT_NAMESPACE).endMetadata().addToData("color", "green").build();
		util.createAndWait(RIGHT_NAMESPACE, rightConfigMap, null);

		// comes from handler::onAdd (and as such from "onEvent")
		Commons.assertReloadLogStatements("ConfigMap service-wiremock was added in namespace left", "",
				"spring-cloud-kubernetes-configuration-watcher");

		// comes from handler::onAdd (and as such from "onEvent")
		Commons.assertReloadLogStatements("ConfigMap service-wiremock was added in namespace right", "",
				"spring-cloud-kubernetes-configuration-watcher");

		await().atMost(Duration.ofSeconds(30)).until(
				() -> !WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh"))).isEmpty());
		WireMock.verify(WireMock.exactly(2), WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));

		testSecretActuatorRefreshMultipleNamespaces();

	}

	/**
	 * <pre>
	 *     - deploy config-watcher in default namespace
	 *     - deploy wiremock in default namespace (so that we could assert calls to the actuator path)
	 *     - deploy secret-left in left namespaces with proper label and "service-wiremock". Because of the
	 *       label, this will trigger a reload; because of the name this will trigger a reload against that name.
	 *       This is a http refresh against the actuator.
	 *     - same as above for the secret-right.
	 * </pre>
	 */
	void testSecretActuatorRefreshMultipleNamespaces() {
		await().timeout(Duration.ofSeconds(60)).ignoreException(SocketException.class)
				.until(() -> WireMock
						.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
								.willReturn(WireMock.aResponse().withBody("{}").withStatus(200)))
						.getResponse().wasConfigured());

		// left-secret
		V1Secret leftSecret = new V1SecretBuilder().editOrNewMetadata()
				.withLabels(Map.of("spring.cloud.kubernetes.secret", "true")).withName("service-wiremock")
				.withNamespace(LEFT_NAMESPACE).endMetadata()
				.addToData("color", Base64.getEncoder().encode("purple".getBytes(StandardCharsets.UTF_8))).build();
		util.createAndWait(LEFT_NAMESPACE, null, leftSecret);

		// right-secret
		V1Secret rightSecret = new V1SecretBuilder().editOrNewMetadata()
				.withLabels(Map.of("spring.cloud.kubernetes.secret", "true")).withName("service-wiremock")
				.withNamespace(RIGHT_NAMESPACE).endMetadata()
				.addToData("color", Base64.getEncoder().encode("green".getBytes(StandardCharsets.UTF_8))).build();
		util.createAndWait(RIGHT_NAMESPACE, null, rightSecret);

		// comes from handler::onAdd (and as such from "onEvent")
		Commons.assertReloadLogStatements("Secret service-wiremock was added in namespace left", "",
				"spring-cloud-kubernetes-configuration-watcher");

		// comes from handler::onAdd (and as such from "onEvent")
		Commons.assertReloadLogStatements("Secret service-wiremock was added in namespace right", "",
				"spring-cloud-kubernetes-configuration-watcher");

		await().atMost(Duration.ofSeconds(30)).until(
				() -> !WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh"))).isEmpty());
		WireMock.verify(WireMock.exactly(4), WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));

	}

	private static void configWatcher(Phase phase) {
		V1ConfigMap configMap = (V1ConfigMap) util
				.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
		V1Deployment deployment = (V1Deployment) util
				.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-deployment.yaml");

		List<V1EnvVar> envVars = List.of(
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_0").value(LEFT_NAMESPACE),
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_1").value(RIGHT_NAMESPACE),
				new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK").value("TRACE"));

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		V1Service service = (V1Service) util
				.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(DEFAULT_NAMESPACE, configMap, null);
			util.createAndWait(DEFAULT_NAMESPACE, null, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(DEFAULT_NAMESPACE, configMap, null);
			util.deleteAndWait(DEFAULT_NAMESPACE, deployment, service, null);
		}

	}

}
