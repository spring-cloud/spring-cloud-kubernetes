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

package org.springframework.cloud.kubernetes.client.secrets.event.reload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
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

class DataChangesInSecretsReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-secrets-event-reload";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api api;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		api = new CoreV1Api();

		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE));
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

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
	@Test
	void testSimple() {
		manifests(Phase.CREATE);
		Commons.assertReloadLogStatements("added secret informer for namespace",
				"added configmap informer for namespace", IMAGE_NAME);

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

		String logs = logs();
		Assertions.assertTrue(logs.contains("Secret event-reload was updated in namespace default"));
		Assertions.assertTrue(logs.contains("data in secret has not changed, will not reload"));

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

		manifests(Phase.DELETE);
	}

	private static void manifests(Phase phase) {

		try {

			V1Secret secret = (V1Secret) util.yaml("secret.yaml");
			V1Deployment deployment = (V1Deployment) util.yaml("deployment.yaml");
			V1Service service = (V1Service) util.yaml("service.yaml");
			V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");

			List<V1EnvVar> envVars = new ArrayList<>(
					Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
							.orElse(List.of()));

			V1EnvVar configDisabledEnvVar = new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_CONFIG_ENABLED")
					.value("FALSE");
			envVars.add(configDisabledEnvVar);
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

			if (phase.equals(Phase.CREATE)) {
				util.createAndWait(NAMESPACE, null, secret);
				util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
			}

			if (phase.equals(Phase.DELETE)) {
				util.deleteAndWait(NAMESPACE, null, secret);
				util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private String logs() {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	private static void replaceSecret(V1Secret secret, String name) {
		try {
			api.replaceNamespacedSecret(name, NAMESPACE, secret, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

}
