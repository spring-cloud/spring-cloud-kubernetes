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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class Fabric8DiscoveryClientHealthIT {

	private static final String REACTIVE_STATUS = "$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].status";

	private static final String BLOCKING_STATUS = "$.components.discoveryComposite.components.discoveryClient.status";

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static KubernetesClient client;

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(Fabric8DiscoveryClientHealthIT.class);

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
	 * Reactive is disabled, only blocking is active. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	@Test
	void testBlockingConfiguration() {

		manifests(true, false, Phase.CREATE);

		assertLogStatement("Will publish InstanceRegisteredEvent from blocking implementation");
		assertLogStatement("publishing InstanceRegisteredEvent");
		assertLogStatement("Discovery Client has been initialized");
		assertLogStatement(
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-fabric8-client-discovery");

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
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(REACTIVE_STATUS);

		manifests(true, false, Phase.DELETE);
	}

	/**
	 * Both blocking and reactive are enabled.
	 */
	@Test
	void testDefaultConfiguration() {

		manifests(false, false, Phase.CREATE);

		assertLogStatement("Will publish InstanceRegisteredEvent from blocking implementation");
		assertLogStatement("publishing InstanceRegisteredEvent");
		assertLogStatement("Discovery Client has been initialized");
		assertLogStatement("received InstanceRegisteredEvent from pod with 'app' label value : "
				+ "spring-cloud-kubernetes-fabric8-client-discovery");

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
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].status")
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes");

		manifests(false, false, Phase.DELETE);
	}

	/**
	 * Reactive is enabled, blocking is disabled. As such,
	 * KubernetesInformerDiscoveryClientAutoConfiguration::indicatorInitializer will post
	 * an InstanceRegisteredEvent.
	 *
	 * We assert for logs and call '/health' endpoint to see that blocking discovery
	 * client was initialized.
	 */
	@Test
	void testReactiveConfiguration() {

		manifests(false, true, Phase.CREATE);

		assertLogStatement("Will publish InstanceRegisteredEvent from reactive implementation");
		assertLogStatement("publishing InstanceRegisteredEvent");
		assertLogStatement("Discovery Client has been initialized");
		assertLogStatement(
				"received InstanceRegisteredEvent from pod with 'app' label value : spring-cloud-kubernetes-fabric8-client-discovery");

		WebClient healthClient = builder().baseUrl("http://localhost/actuator/health").build();

		String healthResult = healthClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult))
				.extractingJsonPathStringValue("$.components.reactiveDiscoveryClients.status").isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(REACTIVE_STATUS)
				.isEqualTo("UP");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathArrayValue(
				"$.components.reactiveDiscoveryClients.components.['Fabric8 Kubernetes Reactive Discovery Client'].details.services")
				.containsExactlyInAnyOrder("spring-cloud-kubernetes-fabric8-client-discovery", "kubernetes");

		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).doesNotHaveJsonPath(BLOCKING_STATUS);

		// test for services also:

		WebClient servicesClient = builder().baseUrl("http://localhost/reactive/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertThat(servicesResult).contains("spring-cloud-kubernetes-fabric8-client-discovery");
		Assertions.assertThat(servicesResult).contains("kubernetes");

		manifests(false, true, Phase.DELETE);
	}

	private static void manifests(boolean disableReactive, boolean disableBlocking, Phase phase) {

		InputStream deploymentStream = util.inputStream("fabric8-discovery-deployment.yaml");
		InputStream serviceStream = util.inputStream("fabric8-discovery-service.yaml");
		InputStream ingressStream = util.inputStream("fabric8-discovery-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).item();
		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());

		EnvVar debugLevelForCommons = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY").withValue("DEBUG")
				.build();

		if (!disableBlocking) {
			EnvVar debugBlockingEnvVar = new EnvVarBuilder()
					.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH").withValue("DEBUG")
					.build();

			EnvVar debugLevelForBlocking = new EnvVarBuilder()
					.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY").withValue("DEBUG")
					.build();

			envVars.add(debugBlockingEnvVar);
			envVars.add(debugLevelForBlocking);
		}

		if (!disableReactive) {
			EnvVar debugReactiveEnvVar = new EnvVarBuilder()
					.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH_REACTIVE")
					.withValue("DEBUG").build();

			EnvVar debugLevelForReactive = new EnvVarBuilder()
					.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY_REACTIVE")
					.withValue("DEBUG").build();

			EnvVar debugLevelForBlocking = new EnvVarBuilder()
					.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY").withValue("DEBUG")
					.build();

			envVars.add(debugReactiveEnvVar);
			envVars.add(debugLevelForBlocking);
			envVars.add(debugLevelForReactive);
		}

		Service service = client.services().load(serviceStream).item();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).item();

		if (disableBlocking) {
			EnvVar disableBlockingEnvVar = new EnvVarBuilder().withName("SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED")
					.withValue("FALSE").build();
			envVars.add(disableBlockingEnvVar);
		}

		if (disableReactive) {
			EnvVar disableReactiveEnvVar = new EnvVarBuilder().withName("SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED")
					.withValue("FALSE").build();
			envVars.add(disableReactiveEnvVar);
		}

		envVars.add(debugLevelForCommons);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private void assertLogStatement(String message) {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			String ok = execResult.getStdout();
			Assertions.assertThat(ok).contains(message);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
