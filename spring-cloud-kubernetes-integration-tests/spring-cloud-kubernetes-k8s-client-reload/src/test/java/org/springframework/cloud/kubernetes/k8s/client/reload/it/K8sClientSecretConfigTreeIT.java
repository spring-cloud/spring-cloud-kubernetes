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

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
class K8sClientSecretConfigTreeIT extends K8sClientReloadBase {

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
		manifestsSecret(Phase.CREATE, util, NAMESPACE, IMAGE_NAME);
		util.configWatcher(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() {
		manifestsSecret(Phase.DELETE, util, NAMESPACE, IMAGE_NAME);
		util.configWatcher(Phase.DELETE);
	}

	/**
	 * <pre>
	 *     - we have "spring.config.import: kubernetes:,configtree:/tmp/", which means we will 'locate' property sources
	 *       from secrets.
	 *     - the property above means that at the moment we will be searching for secrets that only
	 *       match the application name, in this specific test there is no such secrets.
	 *     - what we will also read, is /tmp directory according to configtree rules.
	 *       As such, a property "props.key" will be in environment.
	 *
	 *     - we then change the config map content, wait for configuration watcher to pick up the change
	 *       and schedule a refresh event, based on http.
	 * </pre>
	 */
	@Test
	void test() throws Exception {
		WebClient webClient = builder().baseUrl("http://localhost:32321/secret").build();
		String result = webClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// we first read the initial value from the secret
		assertThat(result).isEqualTo("initial");

		// replace data in secret and wait for k8s to pick it up
		// our polling will detect that and restart the app
		V1Secret secret = (V1Secret) util.yaml("mount/secret.yaml");
		secret.setData(Map.of("application.properties",
				"from.properties.secret.key=as-mount-changed".getBytes(StandardCharsets.UTF_8)));

		// add label so that configuration-watcher picks this up
		Map<String, String> existingLabels = new HashMap<>(
				Optional.ofNullable(secret.getMetadata().getLabels()).orElse(new HashMap<>()));
		existingLabels.put("spring.cloud.kubernetes.secret", "true");
		secret.getMetadata().setLabels(existingLabels);

		// add app annotation
		Map<String, String> existingAnnotations = new HashMap<>(
				Optional.ofNullable(secret.getMetadata().getAnnotations()).orElse(new HashMap<>()));
		existingAnnotations.put("spring.cloud.kubernetes.secret.apps", IMAGE_NAME);
		secret.getMetadata().setAnnotations(existingAnnotations);

		new CoreV1Api().replaceNamespacedSecret("secret-reload", NAMESPACE, secret).execute();

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
