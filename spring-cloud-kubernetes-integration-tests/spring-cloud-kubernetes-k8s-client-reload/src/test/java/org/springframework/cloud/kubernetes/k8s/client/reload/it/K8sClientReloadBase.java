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

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.io.IOException;
import java.io.StringReader;

import io.kubernetes.client.openapi.ApiClient;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class K8sClientReloadBase {

	private static K3sContainer container;

	@BeforeAll
	protected static void beforeAll(K3sContainer k3sContainer) {
		container =  k3sContainer;
	}

	protected static ApiClient apiClient() {
		String kubeConfigYaml = container.getKubeConfigYaml();

		ApiClient client;
		try {
			client = Config.fromConfig(new StringReader(kubeConfigYaml));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return client;
	}

	/**
	 * assert that 'left' is present, and IFF it is, assert that 'right' is not
	 */
	static void assertReloadLogStatements(String left, String right, CapturedOutput output) {

		Awaitilities.awaitUntil(30, 1000, () -> {
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
					configMap)
				.execute();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

//	protected static void manifests(Phase phase, NativeClientKubernetesFixture k8sNativeKubernetesFixture,
//			String namespace, String imageName) {
//
//		V1Deployment deployment = NativeClientKubernetesFixture.yaml("mount/deployment.yaml", V1Deployment.class);
//		V1Service service = NativeClientKubernetesFixture.yaml("mount/service.yaml", V1Service.class);
//		V1ConfigMap configMap = NativeClientKubernetesFixture.yaml("mount/configmap.yaml", V1ConfigMap.class);
//
//		if (phase.equals(Phase.CREATE)) {
//			k8sNativeKubernetesFixture.createAndWait(namespace, configMap, null);
//			k8sNativeKubernetesFixture.createAndWait(namespace, imageName, deployment, service, true);
//		}
//		else {
//			k8sNativeKubernetesFixture.deleteAndWait(namespace, configMap, null);
//			k8sNativeKubernetesFixture.deleteAndWait(namespace, deployment, service);
//		}
//
//	}

	protected static void manifestsSecret(Phase phase, NativeClientKubernetesFixture fixture,
			String namespace, String imageName) {

		V1Secret secret = fixture.yaml("mount/secret.yaml", V1Secret.class);
		V1Deployment deployment = fixture.yaml("mount/deployment-with-secret.yaml",
				V1Deployment.class);
		V1Service service = fixture.yaml("mount/service-with-secret.yaml", V1Service.class);

		if (phase.equals(Phase.CREATE)) {
			fixture.createAndWait(namespace, null, secret);
			fixture.createAndWait(namespace, imageName, deployment, service, true);
		}
		else {
			fixture.deleteAndWait(namespace, null, secret);
			fixture.deleteAndWait(namespace, deployment, service);
		}

	}

}
