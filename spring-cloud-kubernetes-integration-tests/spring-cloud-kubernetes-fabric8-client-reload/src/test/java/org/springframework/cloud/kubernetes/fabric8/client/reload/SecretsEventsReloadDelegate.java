/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.TestUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.TestUtil.retrySpec;

/**
 * @author wind57
 */
final class SecretsEventsReloadDelegate {

	/**
	 * <pre>
	 *     - secret with no labels and data: from.secret.properties.key = secret-initial exists in namespace default
	 *     - we assert that we can read it correctly first, by invoking localhost/key.
	 *
	 *     - then we change the secret by adding a label, this in turn does not
	 *       change the result of localhost/key, because the data has not changed.
	 *
	 *     - then we change data inside the secret, and we must see the updated value.
	 * </pre>
	 */
	static void testSecretReload(KubernetesClient client, K3sContainer container, String appLabelValue) {
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", appLabelValue);

		WebClient webClient = builder().baseUrl("http://localhost/key-from-secret").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("secret-initial", result);

		Secret secret = new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("letter", "a")).withNamespace("default")
						.withName("event-reload").build())
				.withData(Map.of("application.properties",
						Base64.getEncoder().encodeToString("from.secret.properties.key=secret-initial".getBytes())))
				.build();
		client.secrets().inNamespace("default").resource(secret).createOrReplace();

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/key-from-secret").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "secret-initial".equals(innerResult);
		});

		Commons.waitForLogStatement("Secret event-reload was updated in namespace default", container, appLabelValue);
		Commons.waitForLogStatement("data in secret has not changed, will not reload", container, appLabelValue);

		// change data
		secret = new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").withName("event-reload").build())
				.withData(Map.of("application.properties",
						Base64.getEncoder()
								.encodeToString("from.secret.properties.key=secret-initial-changed".getBytes())))
				.build();

		client.secrets().inNamespace("default").resource(secret).createOrReplace();

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/key-from-secret").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "secret-initial-changed".equals(innerResult);
		});

	}

}
