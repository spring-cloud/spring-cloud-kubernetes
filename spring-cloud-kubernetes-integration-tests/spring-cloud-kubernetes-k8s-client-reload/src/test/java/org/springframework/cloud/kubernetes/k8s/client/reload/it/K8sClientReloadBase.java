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

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class K8sClientReloadBase {

	protected static final String NAMESPACE_RIGHT = "right";

	protected static final K3sContainer K3S = Commons.container();

	protected static Util util;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		util = new Util(K3S);
	}

	protected static ApiClient apiClient() {
		String kubeConfigYaml = K3S.getKubeConfigYaml();

		ApiClient client;
		try {
			client = Config.fromConfig(new StringReader(kubeConfigYaml));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new CoreV1Api(client).getApiClient();
	}

	/**
	 * assert that 'left' is present, and IFF it is, assert that 'right' is not
	 */
	static void assertReloadLogStatements(String left, String right, CapturedOutput output) {

		await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean leftIsPresent = output.getOut().contains(left);
			if (leftIsPresent) {
				boolean rightIsPresent = output.getOut().contains(right);
				return !rightIsPresent;
			}
			return false;
		});
	}

	protected static void replaceConfigMap(CoreV1Api api, V1ConfigMap configMap) {
		try {
			api.replaceNamespacedConfigMap(configMap.getMetadata().getName(), configMap.getMetadata().getNamespace(),
					configMap, null, null, null, null);
		}
		catch (ApiException e) {
			System.out.println(e.getResponseBody());
			throw new RuntimeException(e);
		}
	}

	protected static void manifests(Phase phase, Util util, String namespace, String imageName) {

		V1Deployment deployment = (V1Deployment) util.yaml("mount/deployment.yaml");
		V1Service service = (V1Service) util.yaml("mount/service.yaml");
		V1ConfigMap configMap = (V1ConfigMap) util.yaml("mount/configmap.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(namespace, configMap, null);
			util.createAndWait(namespace, imageName, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(namespace, configMap, null);
			util.deleteAndWait(namespace, deployment, service, null);
		}

	}

	protected static void manifestsSecret(Phase phase, Util util, String namespace, String imageName) {

		V1Deployment deployment = (V1Deployment) util.yaml("mount/deployment-with-secret.yaml");
		V1Service service = (V1Service) util.yaml("mount/service-with-secret.yaml");
		V1Secret secret = (V1Secret) util.yaml("mount/secret.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(namespace, null, secret);
			util.createAndWait(namespace, imageName, deployment, service, null, true);
		}
		else {
			util.deleteAndWait(namespace, null, secret);
			util.deleteAndWait(namespace, deployment, service, null);
		}

	}

}
