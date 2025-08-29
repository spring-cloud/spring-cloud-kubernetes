/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
class K8sClientConfigMapConfigTreeIT extends K8sClientReloadBase {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-reload";

	private static final String CONFIGURATION_WATCHER_IMAGE_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private static final String NAMESPACE = "default";

	@BeforeAll
	static void beforeAllLocal() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Commons.validateImage(CONFIGURATION_WATCHER_IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(CONFIGURATION_WATCHER_IMAGE_NAME, K3S);

		util.setUp(NAMESPACE);
		manifests(Phase.CREATE, util, NAMESPACE, IMAGE_NAME);
		util.configWatcher(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() {
		manifests(Phase.DELETE, util, NAMESPACE, IMAGE_NAME);
		util.configWatcher(Phase.DELETE);
	}

	/**
	 * <pre>
	 *     - we have "spring.config.import: kubernetes:,configtree:/tmp/", which means we will 'locate' property sources
	 *       from config maps.
	 *     - the property above means that at the moment we will be searching for config maps that only
	 *       match the application name, in this specific test there is no such config map.
	 *     - what we will also read, is /tmp directory according to configtree rules.
	 *       As such, a property "props.key" will be in environment.
	 *
	 *     - we then change the config map content, wait for configuration watcher to pick up the change
	 *       and schedule a refresh event, based on http.
	 * </pre>
	 */
	@Test
	// TODO This fails intermittently on Jenkins
	@Disabled
	void test() throws Exception {
		WebClient webClient = builder().baseUrl("http://localhost:32321/configmap").build();
		String result = webClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// we first read the initial value from the configmap
		assertThat(result).isEqualTo("as-mount-initial");

		// replace data in configmap and wait for configuration watcher to pick it up.
		V1ConfigMap configMapConfigTree = (V1ConfigMap) util.yaml("mount/configmap.yaml");
		configMapConfigTree.setData(Map.of("from.properties.configmap.key", "as-mount-changed"));
		// add label so that configuration-watcher picks this up
		Map<String, String> existingLabels = new HashMap<>(
				Optional.ofNullable(configMapConfigTree.getMetadata().getLabels()).orElse(new HashMap<>()));
		existingLabels.put("spring.cloud.kubernetes.config", "true");
		configMapConfigTree.getMetadata().setLabels(existingLabels);

		// add app annotation
		Map<String, String> existingAnnotations = new HashMap<>(
				Optional.ofNullable(configMapConfigTree.getMetadata().getAnnotations()).orElse(new HashMap<>()));
		existingAnnotations.put("spring.cloud.kubernetes.configmap.apps", IMAGE_NAME);
		configMapConfigTree.getMetadata().setAnnotations(existingAnnotations);

		new CoreV1Api().replaceNamespacedConfigMap(configMapConfigTree.getMetadata().getName(),
				configMapConfigTree.getMetadata().getNamespace(), configMapConfigTree, null, null, null, null);

		await().atMost(Duration.ofSeconds(180))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> webClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(String.class)
				.retryWhen(retrySpec())
				.block()
				.equals("as-mount-changed"));
	}

}
