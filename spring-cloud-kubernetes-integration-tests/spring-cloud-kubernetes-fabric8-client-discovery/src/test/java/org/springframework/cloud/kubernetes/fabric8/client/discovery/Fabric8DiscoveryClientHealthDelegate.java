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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.retrySpec;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.waitForLogStatement;

/**
 * @author wind57
 */
final class Fabric8DiscoveryClientHealthDelegate {

	private Fabric8DiscoveryClientHealthDelegate() {

	}

	private static final String REACTIVE_STATUS = "$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].status";

	private static final String BLOCKING_STATUS = "$.components.discoveryComposite.components.discoveryClient.status";

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(
			Fabric8DiscoveryClientHealthDelegate.class);

	/**
	 * Reactive is disabled, only blocking is active. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	static void testBlockingConfiguration(K3sContainer k3sContainer, String imageName) {

		waitForLogStatement("Will publish InstanceRegisteredEvent from blocking implementation", k3sContainer,
				imageName);
		waitForLogStatement("publishing InstanceRegisteredEvent", k3sContainer, imageName);
		waitForLogStatement("Discovery Client has been initialized", k3sContainer, imageName);
		waitForLogStatement(
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-fabric8-client-discovery",
				k3sContainer, imageName);

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
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes",
						"busybox-service", "external-name-service", "service-wiremock");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(REACTIVE_STATUS);

	}

	/**
	 * Both blocking and reactive are enabled.
	 */
	static void testDefaultConfiguration(K3sContainer k3sContainer, String imageName) {

		waitForLogStatement("Will publish InstanceRegisteredEvent from blocking implementation", k3sContainer,
				imageName);
		waitForLogStatement("publishing InstanceRegisteredEvent", k3sContainer, imageName);
		waitForLogStatement("Discovery Client has been initialized", k3sContainer, imageName);
		waitForLogStatement("received InstanceRegisteredEvent from pod with 'app' label value : "
				+ "spring-cloud-kubernetes-fabric8-client-discovery", k3sContainer, imageName);

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
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes",
						"external-name-service", "service-wiremock", "busybox-service");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].status")
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes",
						"external-name-service", "service-wiremock", "busybox-service");
	}

	/**
	 * Reactive is enabled, blocking is disabled. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	static void testReactiveConfiguration(K3sContainer k3sContainer, String imageName) {

		waitForLogStatement("Will publish InstanceRegisteredEvent from reactive implementation", k3sContainer,
				imageName);
		waitForLogStatement("publishing InstanceRegisteredEvent", k3sContainer, imageName);
		waitForLogStatement("Discovery Client has been initialized", k3sContainer, imageName);
		waitForLogStatement(
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-fabric8-client-discovery",
				k3sContainer, imageName);

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(REACTIVE_STATUS)
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes",
						"external-name-service", "service-wiremock", "busybox-service");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(BLOCKING_STATUS);

		// test for services also:

		WebClient servicesClient = builder().baseUrl("http://localhost/reactive/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertThat(servicesResult).contains("spring-cloud-kubernetes-fabric8-client-discovery");
		Assertions.assertThat(servicesResult).contains("kubernetes");

	}

}
