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
@ConditionalOnProperty("single.source.multiple.files.stub")
public class SingleSourceMultipleFilesConfigurationStub {

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

		Map<String, String> one = new HashMap<>();
		one.put("fruit.type", "yummy");
		one.put("fruit.properties", "cool.name=banana");
		one.put("fruit-color.properties", "color.when.raw=green\ncolor.when.ripe=yellow");

		// this is not taken, since "shape" is not an active profile
		one.put("fruit-shape.properties", "shape.when.raw=small-sphere\nshape.when.ripe=bigger-sphere");

		V1ConfigMap configMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("my-configmap").withNamespace("spring-k8s").build())
				.addToData(one).build();

		V1ConfigMapList allConfigMaps = new V1ConfigMapList();
		allConfigMaps.addItemsItem(configMap);

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/configmaps")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allConfigMaps))));
	}

}
