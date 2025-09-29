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

package org.springframework.cloud.kubernetes.client.config.bootstrap.stubs;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Order(0)
@Configuration
@ConditionalOnProperty("fix.1715.enabled")
public class FixFor1715ConfigurationStub {

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

	public static void stubData() {

		V1ConfigMap aByName = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("a-by-name").withNamespace("spring-k8s").build())
			.addToData(Map.of("aByName", "aByName"))
			.build();

		V1ConfigMap aByNameAndProfile = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("a-by-name-k8s").withNamespace("spring-k8s").build())
			.addToData(Map.of("aByNameK8s", "aByNameK8s"))
			.build();

		V1ConfigMap aByLabel = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("a-by-label")
				.withNamespace("spring-k8s")
				.withLabels(Map.of("color", "blue"))
				.build())
			.addToData(Map.of("aByLabel", "aByLabel"))
			.build();

		V1ConfigMap aByLabelAndProfile = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("a-by-label-k8s")
				.withNamespace("spring-k8s")
				.withLabels(Map.of("color", "blue"))
				.build())
			.addToData(Map.of("aByLabel", "aByLabel"))
			.build();

		// the actual stub for CoreV1Api calls
		V1ConfigMapList configMapList = new V1ConfigMapList();
		configMapList.addItemsItem(aByName);
		configMapList.addItemsItem(aByNameAndProfile);
		configMapList.addItemsItem(aByLabel);
		configMapList.addItemsItem(aByLabelAndProfile);

		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/configmaps")
			.willReturn(WireMock.aResponse().withStatus(200).withBody(JSON.serialize(configMapList))));
	}

}
