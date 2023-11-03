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

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigurationWatcherMultipleAppsIT {

	private static final String CONFIG_WATCHER_APP_A_IMAGE = "kafka-configmap-app-a";

	private static final String CONFIG_WATCHER_APP_B_IMAGE = "kafka-configmap-app-b";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String CONFIG_MAP_NAME = "multiple-apps";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		util = new Util(K3S);

		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);

		Commons.validateImage(CONFIG_WATCHER_APP_A_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_A_IMAGE, K3S);

		Commons.validateImage(CONFIG_WATCHER_APP_B_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_B_IMAGE, K3S);

		util = new Util(K3S);
		util.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.cleanUp(CONFIG_WATCHER_APP_A_IMAGE, K3S);
		Commons.cleanUp(CONFIG_WATCHER_APP_B_IMAGE, K3S);
		Commons.systemPrune();
	}

	@BeforeEach
	void setup() {
		util.kafka(NAMESPACE, Phase.CREATE);
		appA(Phase.CREATE);
		appB(Phase.CREATE);
		configWatcher(Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.kafka(NAMESPACE, Phase.DELETE);
		appA(Phase.DELETE);
		appB(Phase.DELETE);
		configWatcher(Phase.DELETE);
	}

	@Test
	void testRefresh() {

		// configmap has one label, one that says that we should refresh
		// and one annotation that says that we should refresh some specific services
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata().withName(CONFIG_MAP_NAME)
				.addToLabels("spring.cloud.kubernetes.config", "true")
				.addToAnnotations("spring.cloud.kubernetes.configmap.apps",
						"spring-cloud-kubernetes-client-configuration-watcher-configmap-app-a, "
								+ "spring-cloud-kubernetes-client-configuration-watcher-configmap-app-b")
				.endMetadata().addToData("foo", "hello world").build();
		util.createAndWait(NAMESPACE, configMap, null);

		WebClient.Builder builderA = builder();
		WebClient serviceClientA = builderA.baseUrl("http://localhost:80/app-a").build();

		WebClient.Builder builderB = builder();
		WebClient serviceClientB = builderB.baseUrl("http://localhost:80/app-b").build();

		Boolean[] valueA = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {
			valueA[0] = serviceClientA.method(HttpMethod.GET).retrieve().bodyToMono(Boolean.class)
					.retryWhen(retrySpec()).block();
			return valueA[0];
		});

		Assertions.assertTrue(valueA[0]);

		Boolean[] valueB = new Boolean[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {
			valueB[0] = serviceClientB.method(HttpMethod.GET).retrieve().bodyToMono(Boolean.class)
					.retryWhen(retrySpec()).block();
			return valueB[0];
		});

		Assertions.assertTrue(valueB[0]);
		util.deleteAndWait(NAMESPACE, configMap, null);
	}

	private void appA(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("app-a/app-a-deployment.yaml");
		V1Service service = (V1Service) util.yaml("app-a/app-a-service.yaml");
		V1Ingress ingress = (V1Ingress) util
				.yaml("ingress/spring-cloud-kubernetes-configuration-watcher-multiple-apps-ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}
	}

	private void appB(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("app-b/app-b-deployment.yaml");
		V1Service service = (V1Service) util.yaml("app-b/app-b-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, null, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, null);
		}
	}

	private void configWatcher(Phase phase) {
		V1Deployment deployment = (V1Deployment) util
				.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-bus-kafka-deployment.yaml");
		V1Service service = (V1Service) util
				.yaml("config-watcher/spring-cloud-kubernetes-configuration-watcher-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, null, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, null);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(240, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
