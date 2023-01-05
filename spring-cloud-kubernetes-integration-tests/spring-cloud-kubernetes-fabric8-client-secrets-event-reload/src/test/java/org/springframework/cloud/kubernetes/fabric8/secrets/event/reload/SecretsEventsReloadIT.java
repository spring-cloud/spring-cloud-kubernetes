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

package org.springframework.cloud.kubernetes.fabric8.secrets.event.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

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
class SecretsEventsReloadIT {

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
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void after() throws Exception {
		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@Test
	void test() {
		WebClient webClient = builder().baseUrl("localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the secret
		Assertions.assertEquals("initial", result);

		// then deploy a new version of the secret
		// since we poll and have reload in place, the new property must be visible
		Secret secret = new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").withName("event-reload").build())
				.withData(Map.of("application.properties",
						Base64.getEncoder().encodeToString("from.properties.key=after-change".getBytes())))
				.build();

		client.secrets().inNamespace("default").resource(secret).createOrReplace();

		await().timeout(Duration.ofSeconds(120)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(retrySpec()).block().equals("after-change"));

	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream secretStream = util.inputStream("secret.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();
		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();
		Secret secret = client.secrets().load(secretStream).get();

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, secret);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, null, secret);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
