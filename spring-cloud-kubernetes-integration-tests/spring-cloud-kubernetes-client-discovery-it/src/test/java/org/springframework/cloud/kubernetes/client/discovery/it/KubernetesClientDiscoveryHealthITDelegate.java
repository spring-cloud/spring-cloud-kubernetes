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
import java.util.Objects;

import org.assertj.core.api.Assertions;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

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

		Commons.waitForLogStatement("Will publish InstanceRegisteredEvent from blocking implementation", container,
				IMAGE_NAME);
		Commons.waitForLogStatement("publishing InstanceRegisteredEvent", container, IMAGE_NAME);
		Commons.waitForLogStatement("Discovery Client has been initialized", container, IMAGE_NAME);
		Commons.waitForLogStatement("received InstanceRegisteredEvent from pod with 'app' label value : "
				+ "spring-cloud-kubernetes-client-discovery-it", container, IMAGE_NAME);

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
	void testReactiveConfiguration(K3sContainer container) {

		KubernetesClientDiscoveryClientUtils.patchForReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);

		Commons.waitForLogStatement("Will publish InstanceRegisteredEvent from reactive implementation", container,
				IMAGE_NAME);
		Commons.waitForLogStatement("publishing InstanceRegisteredEvent", container, IMAGE_NAME);
		Commons.waitForLogStatement("Discovery Client has been initialized", container, IMAGE_NAME);
		Commons.waitForLogStatement("received InstanceRegisteredEvent from pod with 'app' label value "
				+ ": spring-cloud-kubernetes-client-discovery-it", container, IMAGE_NAME);

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
	void testDefaultConfiguration(K3sContainer container) {

		KubernetesClientDiscoveryClientUtils.patchForBlockingAndReactiveHealth(DEPLOYMENT_NAME, NAMESPACE);

		Commons.waitForLogStatement("Will publish InstanceRegisteredEvent from blocking implementation", container,
				IMAGE_NAME);
		Commons.waitForLogStatement("publishing InstanceRegisteredEvent", container, IMAGE_NAME);
		Commons.waitForLogStatement("Discovery Client has been initialized", container, IMAGE_NAME);
		Commons.waitForLogStatement("received InstanceRegisteredEvent from pod with 'app' label value : "
				+ "spring-cloud-kubernetes-client-discovery-it", container, IMAGE_NAME);

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();
		WebClient infoClient = builder().baseUrl("http://localhost/actuator/info").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		String infoResult = infoClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

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

		// assert health/info also
		assertHealth(healthResult);
		assertInfo(infoResult);
	}

	private void assertHealth(String healthResult) {
		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.hostIp").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathBooleanValue("$.components.kubernetes.details.inside").isEqualTo(true);

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.labels.app")
				.isEqualTo("spring-cloud-kubernetes-client-discovery-it");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.namespace").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.nodeName").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.podIp").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.podName").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.kubernetes.details.serviceAccount").isNotEmpty();
	}

	private void assertInfo(String infoResult) {
		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult)).extractingJsonPathStringValue("$.kubernetes.hostIp")
				.isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult)).extractingJsonPathBooleanValue("$.kubernetes.inside")
				.isEqualTo(true);

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult))
				.extractingJsonPathStringValue("$.kubernetes.namespace").isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult)).extractingJsonPathStringValue("$.kubernetes.nodeName")
				.isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult)).extractingJsonPathStringValue("$.kubernetes.podIp")
				.isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult)).extractingJsonPathStringValue("$.kubernetes.podName")
				.isNotEmpty();

		Assertions.assertThat(BASIC_JSON_TESTER.from(infoResult))
				.extractingJsonPathStringValue("$.kubernetes.serviceAccount").isNotEmpty();
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
