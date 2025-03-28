/*
 * Copyright 2013-2025 the original author or authors.
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

import java.util.List;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.configureWireMock;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.verifyActuatorCalled;

/**
 * @author Ryan Baxter
 */
class ActuatorRefreshIT {

	private static final String WIREMOCK_PATH = "/";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
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
	 * This test loads two services: wiremock on port 8080 and configuration-watcher on
	 * port 8888. We deploy configuration-watcher first and configure its env variables
	 * that we need for this test. Then, we mock the call to actuator/refresh endpoint and
	 * deploy a new configmap: "service-wiremock". Because This in turn will trigger a
	 * refresh that we capture and assert for.
	 */
	@Test
	void testActuatorRefresh() {
		configureWireMock();
		createConfigMap(util, NAMESPACE);
		verifyActuatorCalled(1);

		Commons.waitForLogStatement("creating NOOP strategy because reload is disabled", K3S,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		deleteConfigMap(util, NAMESPACE);
	}

	private static void configWatcher(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-deployment.yaml");
		V1Service service = (V1Service) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");

		List<V1EnvVar> envVars = List.of(
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_REFRESHDELAY").value("0"),
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_ENABLED").value("FALSE"),
				new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CONFIGURATION_WATCHER")
					.value("DEBUG"));

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service);
		}

	}

}
