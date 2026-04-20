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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.configureWireMock;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createSecret;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteSecret;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.verifyActuatorCalled;

@NativeClientIntegrationTest(withImages = { "spring-cloud-kubernetes-configuration-watcher" },
		wiremock = @NativeClientIntegrationTest.Wiremock(enabled = true, namespaces = "default", withNodePort = true),
		namespaces = { "left", "right" }, rbacNamespaces = "default",
		configurationWatcher = @NativeClientIntegrationTest.ConfigurationWatcher(enabled = true, refreshDelay = "0",
				reloadEnabled = false, watchNamespaces = { "left", "right" }),
		clusterWideRBAC = @NativeClientIntegrationTest.ClusterWideRBAC(enabled = true,
				serviceAccountNamespace = "default", roleBindingNamespaces = { "left", "right" }))
class ActuatorRefreshMultipleNamespacesIT {

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	@AfterAll
	static void afterAll(NativeClientKubernetesFixture fixture) {
		deleteConfigMap(fixture, "left");
		deleteConfigMap(fixture, "right");
		deleteSecret(fixture, "left");
		deleteSecret(fixture, "right");
	}

	/**
	 * <pre>
	 *     - deploy config-watcher in default namespace
	 *     - deploy wiremock in default namespace
	 *     - deploy 'service-wiremock' configmap/secret in 'left' namespace.
	 *     - deploy 'service-wiremock' configmap/secret in 'right' namespace.
	 *     - each of the above triggers configuration watcher to issue
	 *       calls to /actuator/refresh
	 * </pre>
	 */
	@Test
	void testConfigMapActuatorRefreshMultipleNamespaces(NativeClientKubernetesFixture fixture, K3sContainer container) {
		configureWireMock();

		createConfigMap(fixture, "left");
		createConfigMap(fixture, "right");

		createSecret(fixture, "left");
		createSecret(fixture, "right");

		Commons.waitForLogStatement("ConfigMap service-wiremock was added in namespace left", container,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);
		Commons.waitForLogStatement("ConfigMap service-wiremock was added in namespace right", container,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		Commons.waitForLogStatement("Secret service-wiremock was added in namespace left", container,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);
		Commons.waitForLogStatement("Secret service-wiremock was added in namespace right", container,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		verifyActuatorCalled();
	}

}
