/*
 * Copyright 2013-present the original author or authors.
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.configureWireMock;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.verifyActuatorCalled;

/**
 * @author Ryan Baxter
 */
@NativeClientIntegrationTest(withImages = { "spring-cloud-kubernetes-configuration-watcher" },
		wiremock = @NativeClientIntegrationTest.Wiremock(enabled = true, namespaces = "default", withNodePort = true),
		configurationWatcher = @NativeClientIntegrationTest.ConfigurationWatcher(enabled = true, refreshDelay = "0",
				reloadEnabled = false),
		rbacNamespaces = "default")
class ActuatorRefreshIT {

	private static K3sContainer container;

	@BeforeAll
	static void beforeAll(K3sContainer k3sContainer) {
		container = k3sContainer;
	}

	/*
	 * This test loads two services: wiremock on port 8080 and configuration-watcher on
	 * port 8888. We deploy configuration-watcher first and configure its env variables
	 * that we need for this test. Then, we mock the call to actuator/refresh endpoint and
	 * deploy a new configmap: "service-wiremock". Because This in turn will trigger a
	 * refresh that we capture and assert for.
	 */
	@Test
	void testActuatorRefresh(NativeClientKubernetesFixture fixture) {
		configureWireMock();
		createConfigMap(fixture, "default");
		verifyActuatorCalled(1);

		Commons.waitForLogStatement("creating NOOP strategy because reload is disabled", container,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		deleteConfigMap(fixture, "default");
	}

}
