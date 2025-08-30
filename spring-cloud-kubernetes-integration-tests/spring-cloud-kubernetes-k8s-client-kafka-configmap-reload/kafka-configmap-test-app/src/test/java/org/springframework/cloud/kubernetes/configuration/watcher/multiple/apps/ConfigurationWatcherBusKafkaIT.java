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

package org.springframework.cloud.kubernetes.configuration.watcher.multiple.apps;

import java.time.Duration;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
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
class ConfigurationWatcherBusKafkaIT {

	private static final String CONFIG_WATCHER_APP_IMAGE = "kafka-configmap-app";

	private static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String CONFIG_MAP_NAME = "apps";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		util = new Util(K3S);

		Commons.validateImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME, K3S);

		Commons.validateImage(CONFIG_WATCHER_APP_IMAGE, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIG_WATCHER_APP_IMAGE, K3S);

		Images.loadKafka(K3S);
		util.setUp(NAMESPACE);
	}

	@BeforeEach
	void setup() {
		util.kafka(NAMESPACE, Phase.CREATE);
		app(Phase.CREATE);
		configWatcher(Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.kafka(NAMESPACE, Phase.DELETE);
		app(Phase.DELETE);
		configWatcher(Phase.DELETE);
	}

	/**
	 * <pre>
	 *
	 *     To run locally and debug.
	 *
	 *     - I've enabled "Enable host networking" on docker for mac settings
	 *     - docker run -p 9092:9092 confluentinc/confluent-local:7.9.0
	 *     - docker run --network=host -e 'KAFKA_BROKERS=localhost:9092' redpandadata/console:v2.8.2
	 *     - run 'ConfigurationWatcherApplication' with :
	 *     		SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE=default
	 *     		SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_REFRESHDELAY=1
	 *     		SPRING_MAIN_CLOUDPLATFORM=kubernetes
	 *     		SPRING_PROFILES_ACTIVE=bus-kafka
	 *     		SPRING_CLOUD_BUS_DESTINATION=app-bus-destination
	 *
	 *
	 *     How does this test work:
	 *
	 *     - 'spring.cloud.kubernetes.configmap.apps = app' will tell configuration watcher
	 *       to find a service called 'app'.
	 *       If such a service is found, we will call : BusRefreshTrigger::triggerRefresh
	 *
	 *     - This, in turn, will publish a RefreshRemoteApplicationEvent to a randomly named topic.
	 *       In this test case in kafka such a message will be present
	 *
	 *       {
	 *     		"type": "RefreshRemoteApplicationEvent",
	 *     		"timestamp": 1741365164981,
	 *     		"originService": "spring-cloud-kubernetes-configuration-watcher:bus-kafka:8888:24d2ff91d0af65bb17b551465573db4c",
	 *     		"destinationService": "app:**",
	 *     		"id": "309dcdc5-d876-4213-bc84-198d47d2988a"
	 * 		}
	 *
	 * 	   - our app under test has such a configuration:
	 *
	 *		spring:
	 *   	  application:
	 *     		name: non-app
	 *   	  cloud:
	 *   	    bus:
	 *      	 id: app
	 *
	 *       which means we "listen" on the same topic, and we are the 'destinationService'.
	 *
	 *
	 * </pre>
	 */
	@Test
	void testRefresh() {

		// configmap has one label, one that says that we should refresh
		// and one annotation that says that we should refresh some specific services
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
			.withName(CONFIG_MAP_NAME)
			.addToLabels("spring.cloud.kubernetes.config", "true")
			.addToAnnotations("spring.cloud.kubernetes.configmap.apps", "app")
			.endMetadata()
			.build();
		util.createAndWait(NAMESPACE, configMap, null);

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

		Assertions.assertThat(value[0]).isTrue();

		util.deleteAndWait(NAMESPACE, configMap, null);
	}

	private void app(Phase phase) {
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
		V1Deployment deployment = (V1Deployment) util.yaml("config-watcher/watcher-bus-kafka-deployment.yaml");
		V1Service service = (V1Service) util.yaml("config-watcher/watcher-kus-kafka-service.yaml");

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
