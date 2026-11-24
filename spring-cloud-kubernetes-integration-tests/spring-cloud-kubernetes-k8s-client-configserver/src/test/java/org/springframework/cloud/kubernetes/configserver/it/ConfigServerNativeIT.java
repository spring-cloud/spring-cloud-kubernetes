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

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

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
		fixture.deleteAndWait(NAMESPACE, configServerDeployment, configServerService);
		fixture.deleteAndWait(NAMESPACE, configMap, null);
	}

	@Test
	void servesConfigurationFromConfigMap(NativeClientKubernetesFixture fixture) throws Exception {
		int nodePort = fixture.nodePort(NAMESPACE, CONFIG_SERVER_SERVICE);
		WebClient webClient = builder().baseUrl("http://localhost:" + nodePort + "/native-smoke/default").build();
		ResponseEntity<String> response = webClient.method(HttpMethod.GET)
			.retrieve()
			.toEntity(String.class)
			.retryWhen(retrySpec())
			.block();

		assertThat(response.getStatusCode().value())
			.as("Config Server response body: %s", response.getBody())
			.isEqualTo(200);

		Environment environment = new ObjectMapper().readValue(response.getBody(), Environment.class);
		assertThat(environment.getPropertySources()).anySatisfy(propertySource -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> source = (Map<String, Object>) propertySource.getSource();
			assertThat(source).containsEntry("native.image.smoke", "from-configmap");
		});
	}

}
