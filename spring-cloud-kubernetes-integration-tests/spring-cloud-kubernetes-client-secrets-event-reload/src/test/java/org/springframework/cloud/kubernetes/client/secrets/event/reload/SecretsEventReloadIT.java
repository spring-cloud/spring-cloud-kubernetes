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

package org.springframework.cloud.kubernetes.client.secrets.event.reload;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class SecretsEventReloadIT {

	private static final String PROPERTY_URL = "http://localhost:80/key";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-secrets-event-reload";

	private static final String NAMESPACE = "default";

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
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@AfterEach
	void after() {
		configK8sClientIt(Phase.DELETE);
	}

	@Test
	void testSecretReload() throws Exception {
		configK8sClientIt(Phase.CREATE);
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", IMAGE_NAME);
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

	private void configK8sClientIt(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("deployment.yaml");
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

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
