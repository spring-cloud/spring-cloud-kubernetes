/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.it;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author Ryan Baxter
 */
class ConfigMapAndSecretIT {

	private static final String PROPERTY_URL = "localhost:80/myProperty";

	private static final String SECRET_URL = "localhost:80/mySecret";

	private static final String K8S_CONFIG_CLIENT_IT_SERVICE_NAME = "spring-cloud-kubernetes-client-config-it";

	private static final String NAMESPACE = "default";

	private static final String APP_NAME = "spring-cloud-kubernetes-client-config-it";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void setup() throws Exception {
		K3S.start();
		Commons.validateImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
		util = new Util(K3S);
		coreV1Api = new CoreV1Api();
		util.setUp(NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(K8S_CONFIG_CLIENT_IT_SERVICE_NAME, K3S);
	}

	@AfterEach
	void after() {
		configK8sClientIt(false, Phase.DELETE);
	}

	@Test
	void testConfigMapAndSecretWatchRefresh() {
		configK8sClientIt(false, Phase.CREATE);
		testConfigMapAndSecretRefresh();
	}

	@Test
	void testConfigMapAndSecretPollingRefresh() {
		configK8sClientIt(true, Phase.CREATE);
		testConfigMapAndSecretRefresh();
	}

	/**
	 * <pre>
	 *     - read configmap/secrets the way we initially build them and assert their values
	 *     - replace the above and assert we get the new values.
	 * </pre>
	 */
	void testConfigMapAndSecretRefresh() {

		WebClient.Builder builder = builder();
		WebClient propertyClient = builder.baseUrl(PROPERTY_URL).build();

		await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> propertyClient
			.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block().equals("from-config-map"));

		WebClient secretClient = builder.baseUrl(SECRET_URL).build();
		String secret = secretClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals(secret, "p455w0rd");

		V1ConfigMap configMap = (V1ConfigMap) util.yaml("spring-cloud-kubernetes-client-config-it-configmap.yaml");
		Map<String, String> data = configMap.getData();
		data.replace("application.yaml", data.get("application.yaml").replace("from-config-map", "from-unit-test"));
		configMap.data(data);
		try {
			coreV1Api.replaceNamespacedConfigMap(APP_NAME, NAMESPACE, configMap, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
		await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> propertyClient
				.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block().equals("from-unit-test"));
		V1Secret v1Secret = (V1Secret) util.yaml("spring-cloud-kubernetes-client-config-it-secret.yaml");
		Map<String, byte[]> secretData = v1Secret.getData();
		secretData.replace("my.config.mySecret", "p455w1rd".getBytes());
		v1Secret.setData(secretData);
		try {
			coreV1Api.replaceNamespacedSecret(APP_NAME, NAMESPACE, v1Secret, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
		await().timeout(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> secretClient
				.method(HttpMethod.GET).retrieve().bodyToMono(String.class).block().equals("p455w1rd"));
	}

	private static void configK8sClientIt(boolean pooling, Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("spring-cloud-kubernetes-client-config-it-deployment.yaml");

		if (pooling) {
			V1EnvVar one = new V1EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_RELOAD_MODE").withValue("polling")
					.build();
			List<V1EnvVar> existing = new ArrayList<>(
					deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
			existing.add(one);
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);
		}

		V1Service service = (V1Service) util.yaml("spring-cloud-kubernetes-client-config-it-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("spring-cloud-kubernetes-client-config-it-ingress.yaml");

		V1ConfigMap configMap = (V1ConfigMap) util.yaml("spring-cloud-kubernetes-client-config-it-configmap.yaml");
		V1Secret secret = (V1Secret) util.yaml("spring-cloud-kubernetes-client-config-it-secret.yaml");

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
			util.createAndWait(NAMESPACE, configMap, secret);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			util.deleteAndWait(NAMESPACE, configMap, secret);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
