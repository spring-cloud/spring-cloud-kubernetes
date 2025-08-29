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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;

import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
final class TestUtil {

	private static final String WIREMOCK_HOST = "localhost";

	private static final int WIREMOCK_PORT = 32321;

	static final String SPRING_CLOUD_K8S_CONFIG_WATCHER_APP_NAME = "spring-cloud-kubernetes-configuration-watcher";

	private TestUtil() {

	}

	static void configureWireMock() {
		WireMock.configureFor(WIREMOCK_HOST, WIREMOCK_PORT);
		// the above statement configures the client, but we need to make sure the cluster
		// is ready to take a request via 'Wiremock::stubFor' (because sometimes it fails)
		// As such, get the existing mappings and retrySpec() makes sure we retry until
		// we get a response back.
		WebClient client = builder().baseUrl("http://localhost:32321/__admin/mappings").build();
		client.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();

		StubMapping stubMapping = WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
			.willReturn(WireMock.aResponse().withBody("{}").withStatus(200)));

		await().atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.ignoreException(SocketTimeoutException.class)
			.until(() -> stubMapping.getResponse().wasConfigured());
	}

	static void verifyActuatorCalled(int timesCalled) {
		await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			List<LoggedRequest> requests = WireMock
				.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));
			return !requests.isEmpty();
		});
		WireMock.verify(WireMock.exactly(timesCalled),
				WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));
	}

	static void createConfigMap(Util util, String namespace) {
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
			.withName("service-wiremock")
			.withNamespace(namespace)
			.addToLabels("spring.cloud.kubernetes.config", "true")
			.endMetadata()
			.addToData("foo", "bar")
			.build();
		util.createAndWait(namespace, configMap, null);
	}

	static void deleteConfigMap(Util util, String namespace) {
		V1ConfigMap configMap = new V1ConfigMapBuilder().editOrNewMetadata()
			.withName("service-wiremock")
			.withNamespace(namespace)
			.endMetadata()
			.build();
		util.deleteAndWait(namespace, configMap, null);
	}

	static void createSecret(Util util, String namespace) {
		V1Secret secret = new V1SecretBuilder().editOrNewMetadata()
			.withLabels(Map.of("spring.cloud.kubernetes.secret", "true"))
			.withName("service-wiremock")
			.withNamespace(namespace)
			.endMetadata()
			.addToData("color", Base64.getEncoder().encode("purple".getBytes(StandardCharsets.UTF_8)))
			.build();
		util.createAndWait(namespace, null, secret);
	}

	static void deleteSecret(Util util, String namespace) {
		V1Secret secret = new V1SecretBuilder().editOrNewMetadata()
			.withName("service-wiremock")
			.withNamespace(namespace)
			.endMetadata()
			.build();
		util.deleteAndWait(namespace, null, secret);
	}

}
