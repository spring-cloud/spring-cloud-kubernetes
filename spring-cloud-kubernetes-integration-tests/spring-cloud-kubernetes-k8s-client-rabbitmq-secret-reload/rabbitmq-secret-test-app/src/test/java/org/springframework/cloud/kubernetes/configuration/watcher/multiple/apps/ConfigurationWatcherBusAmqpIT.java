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

package org.springframework.cloud.kubernetes.configuration.watcher.multiple.apps;

import java.time.Duration;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigurationWatcherBusAmqpIT {

	private static final String CONFIG_WATCHER_APP_IMAGE = "rabbitmq-secret-app";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();

		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);

		Commons.validateImage(CONFIG_WATCHER_APP_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_IMAGE, K3S);

		Images.loadRabbitmq(K3S);

		util = new Util(K3S);
		util.setUp(NAMESPACE);
	}

	@BeforeEach
	void setup() {
		util.rabbitMq(NAMESPACE, Phase.CREATE);
		appA(Phase.CREATE);
		configWatcher(Phase.CREATE);
	}

	@AfterEach
	void after() {
		util.rabbitMq(NAMESPACE, Phase.DELETE);
		appA(Phase.DELETE);
		configWatcher(Phase.DELETE);
	}

	@Test
	void testRefresh() {

		// secret has one label, one that says that we should refresh
		// and one annotation that says that we should refresh some specific services
		V1Secret secret = new V1SecretBuilder().editOrNewMetadata()
			.withName("secret")
			.addToLabels("spring.cloud.kubernetes.secret", "true")
			.addToAnnotations("spring.cloud.kubernetes.secret.apps", "app")
			.endMetadata()
			.build();
		util.createAndWait(NAMESPACE, null, secret);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:32321/app").build();

		Boolean[] value = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {
			value[0] = serviceClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(Boolean.class)
				.retryWhen(retrySpec())
				.block();
			return value[0];
		});

		Assertions.assertTrue(value[0]);
		util.deleteAndWait(NAMESPACE, null, secret);
	}

	private void appA(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("app/app-deployment.yaml");
		V1Service service = (V1Service) util.yaml("app/app-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service);
		}
	}

	private void configWatcher(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("config-watcher/watcher-deployment.yaml");
		V1Service service = (V1Service) util.yaml("config-watcher/watcher-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(240, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
