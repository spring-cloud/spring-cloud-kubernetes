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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

final class TestAssertions {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(TestAssertions.class);

	private static final String REACTIVE = "$.components.reactiveDiscoveryClients.components.['Kubernetes Reactive Discovery Client']";

	private static final String REACTIVE_STATUS = REACTIVE + ".status";

	private static final String REACTIVE_SERVICES = REACTIVE + ".details.services";

	private static final String BLOCKING = "$.components.discoveryComposite.components.discoveryClient";

	private static final String BLOCKING_STATUS = BLOCKING + ".status";

	private static final String BLOCKING_SERVICES = BLOCKING + ".details.services";

	private TestAssertions() {

	}

	static void assertLogStatement(CapturedOutput output, String textToAssert) {
		await().atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofMillis(200))
			.untilAsserted(() -> assertThat(output.getOut()).contains(textToAssert));
	}

	/**
	 * Reactive is disabled, only blocking is active. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	static void assertBlockingConfiguration(CapturedOutput output, int port) {

		assertLogStatement(output, "Will publish InstanceRegisteredEvent from blocking implementation");
		assertLogStatement(output, "publishing InstanceRegisteredEvent");
		assertLogStatement(output, "Discovery Client has been initialized");

		WebClient healthClient = builder().baseUrl("http://localhost:" + port + "/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(BLOCKING_STATUS).isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(BLOCKING_SERVICES)
			.contains("kubernetes", "service-wiremock")
			.doesNotContain("busybox-service");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(REACTIVE_STATUS);

	}

	/**
	 * Reactive is disabled, only blocking is active. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	static void assertReactiveConfiguration(CapturedOutput output, int port) {

		assertLogStatement(output, "Will publish InstanceRegisteredEvent from reactive implementation");
		assertLogStatement(output, "publishing InstanceRegisteredEvent");
		assertLogStatement(output, "Discovery Client has been initialized");

		WebClient healthClient = builder().baseUrl("http://localhost:" + port + "/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(REACTIVE_STATUS).isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(REACTIVE_SERVICES)
			.containsExactlyInAnyOrder("kubernetes", "service-wiremock");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(BLOCKING_STATUS);

	}

	static void assertPodMetadata(ReactiveDiscoveryClient discoveryClient) {

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock").collectList().block();
		assertThat(serviceInstances).hasSize(1);
		DefaultKubernetesServiceInstance wiremockInstance = (DefaultKubernetesServiceInstance) serviceInstances.get(0);

		assertThat(wiremockInstance.getServiceId()).isEqualTo("service-wiremock");
		assertThat(wiremockInstance.getInstanceId()).isNotNull();
		assertThat(wiremockInstance.getHost()).isNotNull();
		assertThat(wiremockInstance.getMetadata()).isEqualTo(
				Map.of("k8s_namespace", "default", "type", "NodePort", "port.http", "8080", "app", "service-wiremock"));

	}

}
