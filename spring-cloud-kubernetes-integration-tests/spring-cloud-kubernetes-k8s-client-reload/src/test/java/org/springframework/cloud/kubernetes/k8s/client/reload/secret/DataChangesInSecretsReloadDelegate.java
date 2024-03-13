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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.K8sClientSecretsReloadITUtil.builder;
import static org.springframework.cloud.kubernetes.k8s.client.reload.secret.K8sClientSecretsReloadITUtil.retrySpec;

final class DataChangesInSecretsReloadDelegate {

	private static final String NAMESPACE = "default";

	/**
	 * <pre>
	 *     - secret with no labels and data: from.properties.key = initial exists in namespace default
	 *     - we assert that we can read it correctly first, by invoking localhost/key.
	 *
	 *     - then we change the secret by adding a label, this in turn does not
	 *       change the result of localhost/key, because the data has not changed.
	 *
	 *     - then we change data inside the secret, and we must see the updated value.
	 * </pre>
	 */
	static void testDataChangesInSecretsReload(K3sContainer k3sContainer, String deploymentName) {
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", deploymentName);

		WebClient webClient = builder().baseUrl("http://localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the secret
		Assertions.assertEquals("initial", result);

		// then deploy a new version of left-configmap, but without changing its data,
		// only add a label
		V1Secret secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("new-label", "abc")).withNamespace(NAMESPACE)
						.withName("event-reload").build())
				.withData(Map.of("application.properties", "from.properties.key=initial".getBytes())).build();

		replaceSecret(secret, "event-reload");

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/key").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "initial".equals(innerResult);
		});

		Commons.waitForLogStatement("Secret event-reload was updated in namespace default", k3sContainer,
				deploymentName);
		Commons.waitForLogStatement("data in secret has not changed, will not reload", k3sContainer, deploymentName);

		// change data
		secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("new-label", "abc")).withNamespace(NAMESPACE)
						.withName("event-reload").build())
				.withData(Map.of("application.properties", "from.properties.key=change-initial".getBytes())).build();

		replaceSecret(secret, "event-reload");

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/key").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "change-initial".equals(innerResult);
		});

	}

	private static void replaceSecret(V1Secret secret, String name) {
		try {
			new CoreV1Api().replaceNamespacedSecret(name, NAMESPACE, secret, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

}
