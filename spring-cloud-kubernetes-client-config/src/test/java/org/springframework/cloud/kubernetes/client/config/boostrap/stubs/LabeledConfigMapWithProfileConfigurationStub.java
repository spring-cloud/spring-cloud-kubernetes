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

import java.util.Collections;
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
@ConditionalOnProperty("labeled.config.map.with.profile.stub")
public class LabeledConfigMapWithProfileConfigurationStub {

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
	 *     - configmap with name "color-configmap", with labels: "{color: blue}" and "explicitPrefix: blue"
	 *     - configmap with name "green-configmap", with labels: "{color: green}" and "explicitPrefix: blue-again"
	 *     - configmap with name "red-configmap", with labels "{color: not-red}" and "useNameAsPrefix: true"
	 *     - configmap with name "yellow-configmap" with labels "{color: not-yellow}" and useNameAsPrefix: true
	 *     - configmap with name "color-configmap-k8s", with labels : "{color: not-blue}"
	 *     - configmap with name "green-configmap-k8s", with labels : "{color: green-k8s}"
	 *     - configmap with name "green-configmap-prod", with labels : "{color: green-prod}"
	 *
	 *     # a test that proves order: first read non-profile based configmaps, thus profile based
	 *     # configmaps override non-profile ones.
	 *     - configmap with name "green-purple-configmap", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - configmap with name "green-purple-configmap-k8s", labels "{color: black}", data: "{eight: eight-ish}"
	 * </pre>
	 */
	public static void stubData() {

		// is found by labels
		V1ConfigMap colorConfigMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("color-configmap").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "blue")).build())
				.addToData(Collections.singletonMap("one", "1")).build();

		// is not taken, since "profileSpecificSources=false" for the above
		V1ConfigMap colorConfigMapK8s = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("color-configmap-k8s").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "not-blue")).build())
				.addToData(Collections.singletonMap("five", "5")).build();

		// is found by labels
		V1ConfigMap greenConfigMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-configmap").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green")).build())
				.addToData(Collections.singletonMap("two", "2")).build();

		V1ConfigMap greenConfigMapK8s = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-configmap-k8s").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green-k8s")).build())
				.addToData(Collections.singletonMap("six", "6")).build();

		// is taken because prod profile is active and "profileSpecificSources=true"
		V1ConfigMap greenConfigMapProd = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-configmap-prod").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green-prod")).build())
				.addToData(Collections.singletonMap("seven", "7")).build();

		// not taken
		V1ConfigMap redConfigMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("red-configmap").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "red")).build())
				.addToData(Collections.singletonMap("three", "3")).build();

		// not taken
		V1ConfigMap yellowConfigMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("yellow-configmap").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "yellow")).build())
				.addToData(Collections.singletonMap("four", "4")).build();

		// is found by labels
		V1ConfigMap greenPurpleConfigMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-purple-configmap").withNamespace("spring-k8s")
						.withLabels(Map.of("color", "green", "shape", "round")).build())
				.addToData(Collections.singletonMap("eight", "8")).build();

		// is taken and thus overrides the above
		V1ConfigMap greenPurpleConfigMapK8s = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green-purple-configmap-k8s")
						.withNamespace("spring-k8s").withLabels(Map.of("color", "black")).build())
				.addToData(Collections.singletonMap("eight", "eight-ish")).build();

		// the actual stub for CoreV1Api calls
		V1ConfigMapList configMaps = new V1ConfigMapList();
		configMaps.addItemsItem(colorConfigMap);
		configMaps.addItemsItem(colorConfigMapK8s);
		configMaps.addItemsItem(greenConfigMap);
		configMaps.addItemsItem(greenConfigMapK8s);
		configMaps.addItemsItem(greenConfigMapProd);
		configMaps.addItemsItem(redConfigMap);
		configMaps.addItemsItem(yellowConfigMap);
		configMaps.addItemsItem(greenPurpleConfigMap);
		configMaps.addItemsItem(greenPurpleConfigMapK8s);

		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/configmaps")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(configMaps))));
	}

}
