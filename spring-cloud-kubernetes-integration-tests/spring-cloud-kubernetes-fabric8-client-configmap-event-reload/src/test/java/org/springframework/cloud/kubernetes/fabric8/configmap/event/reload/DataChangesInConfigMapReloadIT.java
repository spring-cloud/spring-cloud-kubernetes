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

package org.springframework.cloud.kubernetes.fabric8.configmap.event.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
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

class DataChangesInConfigMapReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-event-reload";

	private static final String NAMESPACE = "default";

	private static final String LEFT_NAMESPACE = "left";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static KubernetesClient client;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();

		util.createNamespace(LEFT_NAMESPACE);
		util.setUpClusterWide(NAMESPACE, Set.of(LEFT_NAMESPACE));
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.deleteNamespace(LEFT_NAMESPACE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	/**
	 * <pre>
	 *     - configMap with no labels and data: left.value = left-initial exists in namespace left
	 *     - we assert that we can read it correctly first, by invoking localhost/left
	 *
	 *     - then we change the configmap by adding a label, this in turn does not
	 *       change the result of localhost/left, because the data has not changed.
	 *
	 *     - then we change data inside the config map, and we must see the updated value
	 * </pre>
	 */
	@Test
	void testSimple() {
		manifests(Phase.CREATE);
		Commons.assertReloadLogStatements("added configmap informer for namespace",
				"added secret informer for namespace", IMAGE_NAME);

		WebClient webClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then deploy a new version of left-configmap, but without changing its data,
		// only add a label
		ConfigMap configMap = new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder()
				.withLabels(Map.of("new-label", "abc")).withNamespace("left").withName("left-configmap").build())
				.withData(Map.of("left.value", "left-initial")).build();

		replaceConfigMap(configMap);

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "left-initial".equals(innerResult);
		});

		String logs = logs();
		Assertions.assertTrue(logs.contains("ConfigMap left-configmap was updated in namespace left"));
		Assertions.assertTrue(logs.contains("data in configmap has not changed, will not reload"));

		// change data
		configMap = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("new-label", "abc")).withNamespace("left")
						.withName("left-configmap").build())
				.withData(Map.of("left.value", "left-after-change")).build();

		replaceConfigMap(configMap);

		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("http://localhost/" + LEFT_NAMESPACE).build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			return "left-after-change".equals(innerResult);
		});

		manifests(Phase.DELETE);
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream configmapAsStream = util.inputStream("left-configmap.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).item();

		List<EnvVar> envVars = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		EnvVar activeProfileProperty = new EnvVarBuilder().withName("SPRING_PROFILES_ACTIVE").withValue("one").build();
		envVars.add(activeProfileProperty);

		EnvVar secretsDisabledEnvVar = new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_SECRETS_ENABLED")
				.withValue("FALSE").build();

		EnvVar debugLevel = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD").withName("DEBUG")
				.build();
		envVars.add(debugLevel);

		envVars.add(secretsDisabledEnvVar);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		Service service = client.services().load(serviceStream).item();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).item();
		ConfigMap configMap = client.configMaps().load(configmapAsStream).item();

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(LEFT_NAMESPACE, configMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(LEFT_NAMESPACE, configMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private String logs() {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	private static void replaceConfigMap(ConfigMap configMap) {
		client.configMaps().inNamespace(LEFT_NAMESPACE).resource(configMap).createOrReplace();
	}

}
