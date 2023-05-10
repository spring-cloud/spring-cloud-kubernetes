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

package org.springframework.cloud.kubernetes.fabric8.configmap.polling.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

public class BootstrapEnabledPollingReloadConfigMapMountIT {

	private static final String IMAGE_NAME = "fabric8-client-configmap-polling-and-import-reload";

	private static final String CONFIGURATION_WATCHER_IMAGE_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String NAMESPACE = "default";

	private static Util util;

	private static KubernetesClient client;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Commons.validateImage(CONFIGURATION_WATCHER_IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIGURATION_WATCHER_IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();
		util.setUp(NAMESPACE);
		util.configWatcher(Phase.CREATE);
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void after() throws Exception {
		manifests(Phase.DELETE);
		util.configWatcher(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.cleanUp(CONFIGURATION_WATCHER_IMAGE_NAME, K3S);
	}

	/**
	 * <pre>
	 *     - we have "spring.config.import: kubernetes:,configtree:/tmp/", which means we will 'locate' property sources
	 *       from config maps.
	 *     - the property above means that at the moment we will be searching for config maps that only
	 *       match the application name, in this specific test there is no such config map.
	 *     - what we will also read, is /tmp directory according to configtree rules.
	 *       As such, a property "props.key" (see deployment-mount.yaml) will be in environment.
	 *
	 *     - we then change the config map content, wait for configuration watcher to pick up the change
	 *       and schedule a refresh event, based on http.
	 * </pre>
	 */
	@Test
	void test() {
		WebClient webClient = builder().baseUrl("http://localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("as-mount-initial", result);

		// replace data in configmap and wait for configuration watcher to pick up the
		// change
		InputStream configMapStream = util.inputStream("mount/configmap-mount.yaml");
		ConfigMap configMap = client.configMaps().load(configMapStream).get();
		configMap.setData(Map.of("from.properties", "as-mount-changed"));

		// add label so that configuration-watcher picks this up
		Map<String, String> existingLabels = new HashMap<>(configMap.getMetadata().getLabels());
		existingLabels.put("spring.cloud.kubernetes.config", "true");
		configMap.getMetadata().setLabels(existingLabels);

		// add annotation for which app to send the http event to
		Map<String, String> existingAnnotations = new HashMap<>(configMap.getMetadata().getAnnotations());
		existingAnnotations.put("spring.cloud.kubernetes.configmap.apps",
				"fabric8-client-configmap-polling-and-import-reload");
		configMap.getMetadata().setAnnotations(existingAnnotations);

		client.configMaps().inNamespace("default").resource(configMap).createOrReplace();

		await().timeout(Duration.ofSeconds(360)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(retrySpec()).block().equals("as-mount-changed"));

	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("mount/deployment-mount.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream configMapStream = util.inputStream("mount/configmap-mount.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();
		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();
		ConfigMap configMap = client.configMaps().load(configMapStream).get();

		List<EnvVar> existing = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());

		// bootstrap is enabled, which means that in 'application-with-bootstrap.yaml',
		// config-data support is disabled.
		EnvVar withBootstrapActiveProfile = new EnvVarBuilder().withName("SPRING_PROFILES_ACTIVE")
				.withValue("with-bootstrap").build();
		EnvVar enabledBootstrap = new EnvVarBuilder().withName("SPRING_CLOUD_BOOTSTRAP_ENABLED").withValue("TRUE")
				.build();

		existing.add(withBootstrapActiveProfile);
		existing.add(enabledBootstrap);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, configMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, configMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
