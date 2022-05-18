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

package org.springframework.cloud.kubernetes.client.config.boostrap.stubs;

import java.util.Arrays;
import java.util.Collections;

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
 * context; with some stub data already.
 *
 * @author wind57
 */
@Order(0)
@Configuration
@ConditionalOnProperty("include.profile.specific.sources")
public class IncludeProfileSpecificSourcesConfigurationStub {

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
		V1ConfigMap one = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("config-map-one-dev").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("one.property", "one")).build();

		V1ConfigMap two = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("config-map-two").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("two.property", "two")).build();

		V1ConfigMap twoDev = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("config-map-two-dev").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("two.property", "twoDev")).build();

		V1ConfigMap three = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("config-map-three").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("three.property", "three")).build();

		V1ConfigMap threeDev = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("config-map-three-dev").withNamespace("spring-k8s")
						.withResourceVersion("1").build())
				.addToData(Collections.singletonMap("three.property", "threeDev")).build();

		V1ConfigMapList allConfigMaps = new V1ConfigMapList();
		allConfigMaps.setItems(Arrays.asList(one, two, twoDev, three, threeDev));

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/configmaps")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allConfigMaps))));
	}

}
