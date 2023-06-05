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

package org.springframework.cloud.kubernetes.client.discovery.it;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryHealthITDelegate {

	private static final String REACTIVE_STATUS = "$.components.reactiveDiscoveryClients.components.['Kubernetes Reactive Discovery Client'].status";

	private static final String BLOCKING_STATUS = "$.components.discoveryComposite.components.discoveryClient.status";

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-discovery-it";

	private static final String DEPLOYMENT_NAME = "spring-cloud-kubernetes-client-discovery-deployment-it";

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(
			KubernetesClientDiscoveryHealthITDelegate.class);

	/**
	 * Reactive is disabled, only blocking is active. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	void testBlockingConfiguration(K3sContainer container) {

		assertLogStatement(container, "Will publish InstanceRegisteredEvent from blocking implementation");
		assertLogStatement(container, "publishing InstanceRegisteredEvent");
		assertLogStatement(container, "Discovery Client has been initialized");
		assertLogStatement(container,
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-client-discovery-it");

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.discoveryComposite.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(BLOCKING_STATUS)
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathArrayValue(
						"$.components.discoveryComposite.components.discoveryClient.details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-client-discovery-it", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(REACTIVE_STATUS);

	}

	/**
	 * Reactive is enabled, blocking is disabled. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	void testReactiveConfiguration(Util util, K3sContainer container) {

		KubernetesClientDiscoveryClientUtils.patchForReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		assertLogStatement(container, "Will publish InstanceRegisteredEvent from reactive implementation");
		assertLogStatement(container, "publishing InstanceRegisteredEvent");
		assertLogStatement(container, "Discovery Client has been initialized");
		assertLogStatement(container,
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-client-discovery-it");

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(REACTIVE_STATUS)
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-client-discovery-it", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(BLOCKING_STATUS);

		// test for services also:

		WebClient servicesClient = builder().baseUrl("http://localhost/reactive/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertThat(servicesResult).contains("spring-cloud-kubernetes-client-discovery-it");
		Assertions.assertThat(servicesResult).contains("kubernetes");

	}

	/**
	 * Both blocking and reactive are enabled.
	 */
	void testDefaultConfiguration(Util util, K3sContainer container) {

		KubernetesClientDiscoveryClientUtils.patchForBlockingAndReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);
		util.waitForDeploymentAfterPatch(DEPLOYMENT_NAME, NAMESPACE,
				Map.of("app", "spring-cloud-kubernetes-client-discovery-it"));

		assertLogStatement(container, "Will publish InstanceRegisteredEvent from blocking implementation");
		assertLogStatement(container, "publishing InstanceRegisteredEvent");
		assertLogStatement(container, "Discovery Client has been initialized");
		assertLogStatement(container,
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-client-discovery-it");

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.discoveryComposite.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.discoveryComposite.components.discoveryClient.status")
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathArrayValue(
						"$.components.discoveryComposite.components.discoveryClient.details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-client-discovery-it", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(
				"$.components.reactiveDiscoveryClients.components.['Kubernetes Reactive Discovery Client'].status")
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-client-discovery-it", "kubernetes");

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private void assertLogStatement(K3sContainer container, String message) {
		try {
			String appPodName = container.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			await().pollDelay(Duration.ofSeconds(4)).pollInterval(Duration.ofSeconds(1)).atMost(20, TimeUnit.SECONDS)
					.until(() -> {
						Container.ExecResult execResult = container.execInContainer("sh", "-c",
								"kubectl logs " + appPodName.trim());
						String ok = execResult.getStdout();
						return ok.contains(message);
					});
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
