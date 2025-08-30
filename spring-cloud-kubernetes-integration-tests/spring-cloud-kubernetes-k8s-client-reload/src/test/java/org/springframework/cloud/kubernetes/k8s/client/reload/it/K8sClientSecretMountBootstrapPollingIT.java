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
import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
class K8sClientSecretMountBootstrapPollingIT extends K8sClientReloadBase {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-reload";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void beforeAllLocal() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		coreV1Api = new CoreV1Api();
		util.setUp(NAMESPACE);
		manifestsSecret(Phase.CREATE, util, NAMESPACE, IMAGE_NAME);
	}

	@AfterAll
	static void afterAll() {
		manifestsSecret(Phase.DELETE, util, NAMESPACE, IMAGE_NAME);
	}

	/**
	 * <pre>
	 *     - we have bootstrap enabled
	 *     - we will 'locate' property sources from secrets.
	 *     - there are no explicit secrets to search for, but what we will also read,
	 *     	 is 'spring.cloud.kubernetes.secret.paths', which we have set to
	 *     	 '/tmp/application.properties'
	 *       in this test. That is populated by the volumeMounts (see mount/deployment-with-secret.yaml)
	 *     - we first assert that we are actually reading the path based source
	 *
	 *     - we then change the secret content, wait for k8s to pick it up and replace them
	 *     - our polling will then detect that change, and trigger a reload.
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

		// we first read the initial value from the configmap
		assertThat(result).isEqualTo("initial");

		// replace data in secret and wait for k8s to pick it up
		// our polling will detect that and restart the app
		V1Secret secret = (V1Secret) util.yaml("mount/secret.yaml");
		secret.setData(Map.of("from.properties.secret.key", "as-mount-changed".getBytes(StandardCharsets.UTF_8)));
		coreV1Api.replaceNamespacedSecret("secret-reload", NAMESPACE, secret);

		Commons.waitForLogStatement("Detected change in config maps/secrets, reload will be triggered", K3S,
				IMAGE_NAME);

		await().atMost(Duration.ofSeconds(120))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> webClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(String.class)
				.retryWhen(retrySpec())
				.block()
				.equals("as-mount-changed"));
	}

}
