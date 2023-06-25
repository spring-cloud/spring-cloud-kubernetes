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

package org.springframework.cloud.kubernetes.fabric8.secrets.event.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class DataChangesInSecretsReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-secrets-event-reload";

	private static final String NAMESPACE = "default";

	private static KubernetesClient client;

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();
		util.setUp(NAMESPACE);
	}

	@AfterAll
	static void after() throws Exception {
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
		Assertions.assertEquals("initial", result);

		Secret secret = new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("letter", "a")).withNamespace(NAMESPACE)
						.withName("event-reload").build())
				.withData(Map.of("application.properties",
						Base64.getEncoder().encodeToString("from.properties.key=initial".getBytes())))
				.build();
		client.secrets().inNamespace(NAMESPACE).resource(secret).createOrReplace();

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
		secret = new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace(NAMESPACE).withName("event-reload").build())
				.withData(Map.of("application.properties",
						Base64.getEncoder().encodeToString("from.properties.key=initial-changed".getBytes())))
				.build();

		client.secrets().inNamespace(NAMESPACE).resource(secret).createOrReplace();

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/key").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "initial-changed".equals(innerResult);
		});

		manifests(Phase.DELETE);
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream secretAsStream = util.inputStream("secret.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).item();

		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar activeProfileProperty = new EnvVarBuilder().withName("SPRING_PROFILES_ACTIVE").withValue("one").build();
		envVars.add(activeProfileProperty);

		EnvVar configMapsDisabledEnvVar = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_CONFIG_ENABLED")
				.withValue("FALSE").build();

		EnvVar debugLevel = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD").withName("DEBUG")
				.build();
		envVars.add(debugLevel);

		envVars.add(configMapsDisabledEnvVar);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).item();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).item();
		Secret secret = client.secrets().load(secretAsStream).item();

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, secret);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, null, secret);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
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

}
