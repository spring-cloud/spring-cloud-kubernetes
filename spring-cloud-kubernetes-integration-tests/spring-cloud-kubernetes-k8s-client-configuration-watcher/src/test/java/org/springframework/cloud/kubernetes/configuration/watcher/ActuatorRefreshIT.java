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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author Ryan Baxter
 */
class ActuatorRefreshIT {

	private static final String WIREMOCK_HOST = "localhost";

	private static final String WIREMOCK_PATH = "/";

	private static final int WIREMOCK_PORT = 80;

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() {
		K3S.start();

		Images.loadWiremock(K3S);

		util = new Util(K3S);
		util.setUp(NAMESPACE);

		configWatcher(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() {
		configWatcher(Phase.DELETE);
	}

	@BeforeEach
	void setup() {
		util.wiremock(NAMESPACE, WIREMOCK_PATH, Phase.CREATE);
	}

	@AfterEach
	void after() {
		util.wiremock(NAMESPACE, WIREMOCK_PATH, Phase.DELETE);
	}

	/*
	 * this test loads two services: wiremock on port 8080 and configuration-watcher
	 * on port 8888. we deploy configuration-watcher first and configure it via a
	 * configmap with the same name. then, we mock the call to actuator/refresh endpoint
	 * and deploy a new configmap: "service-wiremock". This in turn will trigger a
	 * refresh that we capture and assert for.
	 */
	@Test
	void testActuatorRefresh() {
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT);
		// the above statement configures the client, but we need to make sure the cluster
		// is ready to take a request via 'Wiremock::stubFor' (because sometimes it fails)
		// As such, get the existing mappings and retrySpec() makes sure we retry until
		// we get a response back.
		WebClient client = builder().baseUrl("http://localhost:80/__admin/mappings").build();
		client.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		StubMapping stubMapping = WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
				.willReturn(WireMock.aResponse().withBody("{}").withStatus(200)));

		await().atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.ignoreException(SocketTimeoutException.class)
			.until(() -> stubMapping.getResponse().wasConfigured());

		createConfigMap();

		// Wait a bit before we verify
		await().atMost(Duration.ofSeconds(30))
			.until(() -> !WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")))
				.isEmpty());
		WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));

		deleteConfigMap();

		// the other test
		testActuatorRefreshReloadDisabled();

	}

	/*
	 * same test as above, but reload is disabled.
	 */
	void testActuatorRefreshReloadDisabled() {

		///TestUtil.patchForDisabledReload(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, NAMESPACE, DOCKER_IMAGE);

		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT);
		await().timeout(Duration.ofSeconds(60))
			.until(() -> WireMock
				.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
					.willReturn(WireMock.aResponse().withBody("{}").withStatus(200)))
				.getResponse()
				.wasConfigured());

		createConfigMap();

		// Wait a bit before we verify
		await().atMost(Duration.ofSeconds(30))
			.until(() -> !WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")))
				.isEmpty());

//		Commons.waitForLogStatement("creating NOOP strategy because reload is disabled", K3S,
//				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		// nothing related to 'ConfigReloadUtil' is present in logs
		// this proves that once we disable reload everything still works
		//Assertions.assertFalse(logs().contains("ConfigReloadUtil"));
		WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));

		deleteConfigMap();

	}

	private static void configWatcher(Phase phase) {
		V1ConfigMap configMap = (V1ConfigMap) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-configmap.yaml");
		V1Deployment deployment = (V1Deployment) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-deployment.yaml");
		V1Service service = (V1Service) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, configMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, configMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, null);
		}

	}

	// Create new configmap to trigger controller to signal app to refresh
	private void createConfigMap() {
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
			.withName("service-wiremock")
			.addToLabels("spring.cloud.kubernetes.config", "true")
			.endMetadata()
			.addToData("foo", "bar")
			.build();
		util.createAndWait(NAMESPACE, configMap, null);
	}

	private void deleteConfigMap() {
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
			.withName("service-wiremock")
			.addToLabels("spring.cloud.kubernetes.config", "true")
			.endMetadata()
			.addToData("foo", "bar")
			.build();
		util.deleteAndWait(NAMESPACE, configMap, null);
	}

//	private String logs() {
//		try {
//			String appPodName = K3S
//				.execInContainer("sh", "-c",
//						"kubectl get pods -l app=" + SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME
//								+ " -o=name --no-headers | tr -d '\n'")
//				.getStdout();
//
//			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
//			return execResult.getStdout();
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}
//	}

}
