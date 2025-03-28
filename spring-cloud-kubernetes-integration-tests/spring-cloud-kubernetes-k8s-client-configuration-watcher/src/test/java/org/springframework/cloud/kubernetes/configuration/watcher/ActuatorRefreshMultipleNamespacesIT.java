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
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.configureWireMock;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.createSecret;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteConfigMap;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.deleteSecret;
import static org.springframework.cloud.kubernetes.configuration.watcher.TestUtil.verifyActuatorCalled;

class ActuatorRefreshMultipleNamespacesIT {

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

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
	static void afterAll() {
		configWatcher(Phase.DELETE);
		util.wiremock(DEFAULT_NAMESPACE, "/", Phase.DELETE);
		util.deleteClusterWide(DEFAULT_NAMESPACE, Set.of(DEFAULT_NAMESPACE, LEFT_NAMESPACE, RIGHT_NAMESPACE));
		util.deleteNamespace(LEFT_NAMESPACE);
		util.deleteNamespace(RIGHT_NAMESPACE);
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
	void testConfigMapActuatorRefreshMultipleNamespaces() {
		configureWireMock();

		createConfigMap(util, LEFT_NAMESPACE);
		createConfigMap(util, RIGHT_NAMESPACE);

		createSecret(util, LEFT_NAMESPACE);
		createSecret(util, RIGHT_NAMESPACE);

		Commons.waitForLogStatement("ConfigMap service-wiremock was added in namespace left", K3S,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);
		Commons.waitForLogStatement("ConfigMap service-wiremock was added in namespace right", K3S,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		Commons.waitForLogStatement("Secret service-wiremock was added in namespace left", K3S,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);
		Commons.waitForLogStatement("Secret service-wiremock was added in namespace right", K3S,
				SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME);

		verifyActuatorCalled(4);
		deleteConfigMap(util, LEFT_NAMESPACE);
		deleteConfigMap(util, RIGHT_NAMESPACE);
		deleteSecret(util, LEFT_NAMESPACE);
		deleteSecret(util, RIGHT_NAMESPACE);
	}

	private static void configWatcher(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-deployment.yaml");

		List<V1EnvVar> envVars = List.of(
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_0").value(LEFT_NAMESPACE),
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_REFRESHDELAY").value("0"),
				new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_1").value(RIGHT_NAMESPACE),
				new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD")
					.value("DEBUG"));

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		V1Service service = (V1Service) util
			.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(DEFAULT_NAMESPACE, null, deployment, service, true);
		}
		else {
			util.deleteAndWait(DEFAULT_NAMESPACE, deployment, service);
		}

	}

}
