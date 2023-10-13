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

package org.springframework.cloud.kubernetes.k8s.client.reload.secret;

import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.DataChangesInSecretsReloadDelegate.testDataChangesInSecretsReload;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.K8sClientSecretsReloadITUtil.builder;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.K8sClientSecretsReloadITUtil.patchOne;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.K8sClientSecretsReloadITUtil.retrySpec;

/**
 * @author wind57
 */
class K8sClientSecretsReloadIT {

	private static final String PROPERTY_URL = "http://localhost:80/key";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-reload";

	private static final String NAMESPACE = "default";

	private static final String DEPLOYMENT_NAME = "spring-k8s-client-reload";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + Commons.pomVersion();

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void setup() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		util = new Util(K3S);
		coreV1Api = new CoreV1Api();
		util.setUp(NAMESPACE);
		configK8sClientIt(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		configK8sClientIt(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	@Test
	void testSecretReload() throws Exception {
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", DEPLOYMENT_NAME);
		testSecretEventReload();

		testAllOther();
	}

	private void testAllOther() throws Exception {
		recreateSecret();
		patchOne(DEPLOYMENT_NAME, NAMESPACE, DOCKER_IMAGE);
		testSecretReloadConfigDisabled();

		recreateSecret();
		patchOne(DEPLOYMENT_NAME, NAMESPACE, DOCKER_IMAGE);
		testDataChangesInSecretsReload(K3S, DEPLOYMENT_NAME);
	}

	void testSecretReloadConfigDisabled() throws Exception {
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", DEPLOYMENT_NAME);
		testSecretEventReload();
	}

	void testSecretEventReload() throws Exception {

		WebClient.Builder builder = builder();
		WebClient secretClient = builder.baseUrl(PROPERTY_URL).build();

		await().timeout(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2))
				.until(() -> secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
						.retryWhen(retrySpec()).block().equals("initial"));

		V1Secret v1Secret = (V1Secret) util.yaml("secret.yaml");
		Map<String, byte[]> secretData = v1Secret.getData();
		secretData.replace("application.properties", "from.properties.key: after-change".getBytes());
		v1Secret.setData(secretData);
		coreV1Api.replaceNamespacedSecret("event-reload", NAMESPACE, v1Secret, null, null, null, null);

		await().timeout(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2))
				.until(() -> secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
						.retryWhen(retrySpec()).block().equals("after-change"));
	}

	private void recreateSecret() {
		V1Secret secret = (V1Secret) util.yaml("secret.yaml");
		util.deleteAndWait(NAMESPACE, null, secret);
		util.createAndWait(NAMESPACE, null, secret);
	}

	private static void configK8sClientIt(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("deployment-with-secret.yaml");
		V1Service service = (V1Service) util.yaml("service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");
		V1Secret secret = (V1Secret) util.yaml("secret.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
			util.createAndWait(NAMESPACE, null, secret);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			util.deleteAndWait(NAMESPACE, null, secret);
		}
	}

}
