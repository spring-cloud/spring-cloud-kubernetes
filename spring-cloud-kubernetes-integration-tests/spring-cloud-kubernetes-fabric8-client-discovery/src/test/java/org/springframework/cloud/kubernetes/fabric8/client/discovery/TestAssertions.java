/*
 * Copyright 2012-present the original author or authors.
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

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static java.util.AbstractMap.SimpleEntry;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
final class TestAssertions {

	private static final String REACTIVE_STATUS = "$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].status";

	private static final String BLOCKING_STATUS = "$.components.discoveryComposite.components.discoveryClient.status";

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(TestAssertions.class);

	private TestAssertions() {

	}

	static void assertPodMetadata(DiscoveryClient discoveryClient) {

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("busybox-service");

		// if annotations are empty, we got the other pod, with labels here
		DefaultKubernetesServiceInstance withCustomLabel = serviceInstances.stream()
			.map(instance -> (DefaultKubernetesServiceInstance) instance)
			.filter(x -> x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		List<Entry<String, String>> podMetadataLabels = withCustomLabel.podMetadata()
			.get("labels")
			.entrySet()
			.stream()
			.toList();

		assertThat(withCustomLabel.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomLabel.getInstanceId()).isNotNull();
		assertThat(withCustomLabel.getHost()).isNotNull();
		assertThat(withCustomLabel.getMetadata())
			.isEqualTo(Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		assertThat(podMetadataLabels).contains(new SimpleEntry<>("my-label", "my-value"));

		// if annotation are present, we got the one with annotations here
		DefaultKubernetesServiceInstance withCustomAnnotation = serviceInstances.stream()
			.map(instance -> (DefaultKubernetesServiceInstance) instance)
			.filter(x -> !x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty())
			.toList()
			.get(0);
		List<Entry<String, String>> podMetadataAnnotations = withCustomAnnotation.podMetadata()
			.get("annotations")
			.entrySet()
			.stream()
			.toList();

		assertThat(withCustomLabel.getServiceId()).isEqualTo("busybox-service");
		assertThat(withCustomLabel.getInstanceId()).isNotNull();
		assertThat(withCustomLabel.getHost()).isNotNull();
		assertThat(withCustomLabel.getMetadata())
			.isEqualTo(Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		assertThat(podMetadataAnnotations).contains(new SimpleEntry<>("my-annotation", "my-value"));
	}

	static void assertAllServices(DiscoveryClient discoveryClient) {

		List<String> services = discoveryClient.getServices();
		assertThat(services).containsExactlyInAnyOrder("kubernetes", "busybox-service", "external-name-service");

		ServiceInstance externalNameInstance = discoveryClient.getInstances("external-name-service").get(0);

		assertThat(externalNameInstance.getServiceId()).isEqualTo("external-name-service");
		assertThat(externalNameInstance.getInstanceId()).isNotNull();
		assertThat(externalNameInstance.getHost()).isEqualTo("spring.io");
		assertThat(externalNameInstance.getPort()).isEqualTo(-1);
		assertThat(externalNameInstance.getMetadata())
			.isEqualTo(Map.of("k8s_namespace", "default", "type", "ExternalName"));
		assertThat(externalNameInstance.isSecure()).isFalse();
		assertThat(externalNameInstance.getUri().toASCIIString()).isEqualTo("spring.io");
		assertThat(externalNameInstance.getScheme()).isNull();
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

		waitForLogStatement(output, "Will publish InstanceRegisteredEvent from blocking implementation");
		waitForLogStatement(output, "publishing InstanceRegisteredEvent");
		waitForLogStatement(output, "Discovery Client has been initialized");

		WebClient healthClient = builder().baseUrl("http://localhost:" + port + "/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		assertThat(BASIC_JSON_TESTER.from(healthResult))
			.extractingJsonPathStringValue("$.components.discoveryComposite.status")
			.isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(BLOCKING_STATUS).isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult))
			.extractingJsonPathArrayValue("$.components.discoveryComposite.components.discoveryClient.details.services")
			.containsExactlyInAnyOrder("kubernetes", "busybox-service");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(REACTIVE_STATUS);

	}

	/**
	 * Reactive is enabled, blocking is disabled. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that reactive discovery
	 * client was initialized.
	 */
	static void testReactiveConfiguration(ReactiveDiscoveryClient discoveryClient, CapturedOutput output, int port) {

		waitForLogStatement(output, "Will publish InstanceRegisteredEvent from reactive implementation");
		waitForLogStatement(output, "publishing InstanceRegisteredEvent");
		waitForLogStatement(output, "Discovery Client has been initialized");

		WebClient healthClient = builder().baseUrl("http://localhost:" + port + "/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		assertThat(BASIC_JSON_TESTER.from(healthResult))
			.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status")
			.isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(REACTIVE_STATUS).isEqualTo("UP");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].details.services")
			.containsExactlyInAnyOrder("kubernetes", "busybox-service");

		assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(BLOCKING_STATUS);

		List<String> services = discoveryClient.getServices().toStream().toList();

		assertThat(services).contains("busybox-service");
		assertThat(services).contains("kubernetes");

	}

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches '^uat.*$'"
	 *
	 *     As such, both services are found via 'getInstances' call.
	 * </pre>
	 */
	static void filterMatchesBothNamespacesViaThePredicate(DiscoveryClient discoveryClient) {

		List<String> services = discoveryClient.getServices();

		assertThat(Objects.requireNonNull(services).size()).isEqualTo(1);
		assertThat(services).contains("service-wiremock");

		List<DefaultKubernetesServiceInstance> serviceInstances = discoveryClient.getInstances("service-wiremock")
			.stream()
			.map(x -> (DefaultKubernetesServiceInstance) x)
			.toList();

		assertThat(Objects.requireNonNull(serviceInstances).size()).isEqualTo(2);
		List<DefaultKubernetesServiceInstance> sorted = serviceInstances.stream()
			.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::getNamespace))
			.toList();

		DefaultKubernetesServiceInstance first = sorted.get(0);
		assertThat(first.getServiceId()).isEqualTo("service-wiremock");
		assertThat(first.getInstanceId()).isNotNull();
		assertThat(first.getPort()).isEqualTo(8080);
		assertThat(first.getNamespace()).isEqualTo("a-uat");
		assertThat(first.getMetadata()).isEqualTo(
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

		DefaultKubernetesServiceInstance second = sorted.get(1);
		assertThat(second.getServiceId()).isEqualTo("service-wiremock");
		assertThat(second.getInstanceId()).isNotNull();
		assertThat(second.getPort()).isEqualTo(8080);
		assertThat(second.getNamespace()).isEqualTo("b-uat");
		assertThat(second.getMetadata()).isEqualTo(
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "b-uat", "type", "ClusterIP"));

	}

	private static void waitForLogStatement(CapturedOutput output, String message) {
		await().pollInterval(Duration.ofSeconds(1))
			.atMost(Duration.ofSeconds(30))
			.until(() -> output.getOut().contains(message));
	}

}
