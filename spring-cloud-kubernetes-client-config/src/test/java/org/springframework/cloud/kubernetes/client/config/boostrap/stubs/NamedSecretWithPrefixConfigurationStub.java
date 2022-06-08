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

import java.util.Arrays;
import java.util.Collections;

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
@ConditionalOnProperty("named.secret.with.prefix.stub")
public class NamedSecretWithPrefixConfigurationStub {

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
		stubData();
		return apiClient;
	}

	public static void stubData() {
		V1Secret one = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("secret-one").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("one.property", "one".getBytes())).build();

		V1Secret two = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("secret-two").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("property", "two".getBytes())).build();

		V1Secret three = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("secret-three").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("property", "three".getBytes())).build();

		V1SecretList allSecrets = new V1SecretList();
		allSecrets.setItems(Arrays.asList(one, two, three));

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/secrets")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allSecrets))));
	}

}
