/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.boostrap.stubs;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A test bootstrap that takes care to initialize ApiClient _before_ our main bootstrap
 * context; with some stub data already present.
 *
 * @author wind57
 */
@Order(0)
@Configuration
@ConditionalOnProperty("labeled.secret.with.profile.stub")
public class LabeledSecretWithProfileConfigurationStub {

	@Bean
	public WireMockServer wireMock() {
		WireMockServer server = new WireMockServer(options().dynamicPort());
		server.start();
		WireMock.configureFor("localhost", server.port());
		return server;
	}

	@Bean
	public ApiClient apiClient(WireMockServer wireMockServer) {
		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
		apiClient.setDebugging(true);
		stubData();
		return apiClient;
	}

	/**
	 * <pre>
	 *     - secret with name "color-secret", with labels: "{color: blue}" and "explicitPrefix: blue"
	 *     - secret with name "green-secret", with labels: "{color: green}" and "explicitPrefix: blue-again"
	 *     - secret with name "red-secret", with labels "{color: not-red}" and "useNameAsPrefix: true"
	 *     - secret with name "yellow-secret" with labels "{color: not-yellow}" and useNameAsPrefix: true
	 *     - secret with name "color-secret-k8s", with labels : "{color: not-blue}"
	 *     - secret with name "green-secret-k8s", with labels : "{color: green-k8s}"
	 *     - secret with name "green-secret-prod", with labels : "{color: green-prod}"
	 *
	 *     # a test that proves order: first read non-profile based secrets, thus profile based
	 *     # secrets override non-profile ones.
	 *     - secret with name "green-purple-secret", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - secret with name "green-purple-secret-k8s", labels "{color: black}", data: "{eight: eight-ish}"
	 * </pre>
	 */
	public static void stubData() {

		// is found by labels
		V1Secret colorSecret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("color-secret").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "blue")).build())
				.addToData(Collections.singletonMap("one", "1".getBytes(StandardCharsets.UTF_8))).build();

		// is not taken, since "profileSpecificSources=false" for the above
		V1Secret colorSecretK8s = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("color-secret-k8s").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "not-blue")).build())
				.addToData(Collections.singletonMap("five", "5".getBytes(StandardCharsets.UTF_8))).build();

		// is found by labels
		V1Secret greenSecret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-secret").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green")).build())
				.addToData(Collections.singletonMap("two", "2".getBytes(StandardCharsets.UTF_8))).build();

		V1Secret greenSecretK8s = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-secret-k8s").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green-k8s")).build())
				.addToData(Collections.singletonMap("six", "6".getBytes(StandardCharsets.UTF_8))).build();

		// is taken because prod profile is active and "profileSpecificSources=true"
		V1Secret shapeSecretProd = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-secret-prod").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green-prod")).build())
				.addToData(Collections.singletonMap("seven", "7".getBytes(StandardCharsets.UTF_8))).build();

		// not taken
		V1Secret redSecret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("red-secret").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "not-red")).build())
				.addToData(Collections.singletonMap("three", "3".getBytes(StandardCharsets.UTF_8))).build();

		// not taken
		V1Secret yellowSecret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("yellow-secret").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "not-yellow")).build())
				.addToData(Collections.singletonMap("four", "4".getBytes(StandardCharsets.UTF_8))).build();

		// is found by labels
		V1Secret greenPurpleSecret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-purple-secret").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green", "shape", "round")).build())
				.addToData(Collections.singletonMap("eight", "8".getBytes(StandardCharsets.UTF_8))).build();

		// is taken and thus overrides the above
		V1Secret greenPurpleSecretK8s = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-purple-secret-k8s").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "black")).build())
				.addToData(Collections.singletonMap("eight", "eight-ish".getBytes(StandardCharsets.UTF_8))).build();

		// the actual stub for CoreV1Api calls
		V1SecretList secrets = new V1SecretList();
		secrets.addItemsItem(colorSecret);
		secrets.addItemsItem(colorSecretK8s);
		secrets.addItemsItem(greenSecret);
		secrets.addItemsItem(greenSecretK8s);
		secrets.addItemsItem(shapeSecretProd);
		secrets.addItemsItem(redSecret);
		secrets.addItemsItem(yellowSecret);
		secrets.addItemsItem(greenPurpleSecret);
		secrets.addItemsItem(greenPurpleSecretK8s);

		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/secrets")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(secrets))));
	}

}
