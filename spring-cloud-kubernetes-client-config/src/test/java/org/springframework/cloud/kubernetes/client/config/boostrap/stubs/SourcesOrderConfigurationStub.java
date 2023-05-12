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

package org.springframework.cloud.kubernetes.client.config.boostrap.stubs;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
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

@Order(0)
@Configuration
@ConditionalOnProperty("sources.order.stub")
public class SourcesOrderConfigurationStub {

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
		stubConfigMapData();
		stubSecretsData();
		return apiClient;
	}

	public static void stubConfigMapData() {

		Map<String, String> configMapData = new HashMap<>();
		configMapData.put("my.key", "from-configmap");
		configMapData.put("my.two", "two");

		V1ConfigMap myConfigMap = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("my-configmap").withNamespace("spring-k8s").withResourceVersion("1").build())
				.addToData(configMapData).build();

		V1ConfigMapList allConfigMaps = new V1ConfigMapList();
		allConfigMaps.setItems(Collections.singletonList(myConfigMap));

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/configmaps")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allConfigMaps))));
	}

	public static void stubSecretsData() {

		Map<String, byte[]> secretData = new HashMap<>();
		secretData.put("my.key", "from-secret".getBytes(StandardCharsets.UTF_8));
		secretData.put("my.one", "one".getBytes(StandardCharsets.UTF_8));

		V1Secret mySecret = new V1SecretBuilder().withMetadata(new V1ObjectMetaBuilder().withName("my-secret")
				.withNamespace("spring-k8s").withResourceVersion("1").build()).addToData(secretData).build();

		V1SecretList allConfigMaps = new V1SecretList();
		allConfigMaps.setItems(Collections.singletonList(mySecret));

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/secrets")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allConfigMaps))));
	}

}
