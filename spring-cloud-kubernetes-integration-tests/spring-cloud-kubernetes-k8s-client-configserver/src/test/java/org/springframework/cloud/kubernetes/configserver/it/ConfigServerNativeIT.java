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

package org.springframework.cloud.kubernetes.configserver.it;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Config Server native image serves configuration from a Kubernetes
 * ConfigMap.
 */
@NativeClientIntegrationTest(rbacNamespaces = "default")
class ConfigServerNativeIT {

	private static final String NAMESPACE = "default";

	private static final String CONFIG_MAP_NAME = "native-smoke";

	private static final String CONFIG_SERVER_SERVICE = "spring-cloud-kubernetes-configserver-native-it";

	private static V1ConfigMap configMap;

	private static V1Deployment configServerDeployment;

	private static V1Service configServerService;

	@BeforeAll
	static void beforeAll(NativeClientKubernetesFixture fixture) {
		configMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData("native-smoke.properties", "native.image.smoke=from-configmap\n")
			.build();
		fixture.createAndWait(NAMESPACE, configMap, null);

		configServerDeployment = fixture.yaml("configserver/configserver-deployment.yaml", V1Deployment.class);
		configServerService = fixture.yaml("configserver/configserver-service.yaml", V1Service.class);
		fixture.createAndWait(NAMESPACE, configServerDeployment, configServerService, true);
	}

	@AfterAll
	static void afterAll(NativeClientKubernetesFixture fixture) {
		if (configServerDeployment != null || configServerService != null) {
			fixture.deleteAndWait(NAMESPACE, configServerDeployment, configServerService);
		}
		if (configMap != null) {
			fixture.deleteAndWait(NAMESPACE, configMap, null);
		}
	}

	@Test
	void servesConfigurationFromConfigMap(NativeClientKubernetesFixture fixture) throws Exception {
		int nodePort = fixture.nodePort(NAMESPACE, CONFIG_SERVER_SERVICE);
		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://localhost:" + nodePort + "/native-smoke/default"))
			.timeout(Duration.ofSeconds(30))
			.GET()
			.build();
		HttpResponse<String> response = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build()
			.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).as("Config Server response body: %s", response.body()).isEqualTo(200);

		JsonNode environment = new ObjectMapper().readTree(response.body());
		List<JsonNode> values = environment.path("propertySources").findValues("native.image.smoke");
		assertThat(values.stream().map(JsonNode::asText).toList()).contains("from-configmap");
	}

}
