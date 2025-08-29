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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapsCache;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsCache;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
abstract class ConfigServerIntegration {

	private static final String SOURCE_NAME = "test-cm";

	private static final String NAMESPACE = "default";

	private static final String TEST_CONFIG_MAP_DEV_PROPERTIES = "test-cm-dev.properties";

	private static final String TEST_CONFIG_MAP_DEV_NAME = "configmap.test-cm.default.dev";

	private static final String TEST_CONFIG_MAP_DEV_DATA = """
				dummy.property.profile=dev
				dummy.property.value=1
				dummy.property.enabled=false
			""";

	private static final String TEST_CONFIG_MAP_QA_PROPERTIES = "test-cm-qa.properties";

	private static final String TEST_CONFIG_MAP_QA_DATA = """
				dummy.property.profile=qa
				dummy.property.value=2
				dummy.property.enabled=true
			""";

	private static final String TEST_CONFIG_MAP_PROD_PROPERTIES = "test-cm-prod.properties";

	private static final String TEST_CONFIG_MAP_PROD_NAME = "configmap.test-cm.default.prod";

	private static final String TEST_CONFIG_MAP_PROD_DATA = """
				dummy.property.profile=prod
				dummy.property.value=3
				dummy.property.enabled=true
			""";

	private static final String TEST_CONFIG_MAP_PROPERTIES = "test-cm.properties";

	private static final String TEST_CONFIG_MAP_NAME = "configmap.test-cm.default.default";

	private static final String TEST_CONFIG_MAP_DATA = """
				dummy.property.profile=default
				dummy.property.value=4
				dummy.property.enabled=true
			""";

	private static final String TEST_SECRET_NAME = "secret.test-cm.default.default";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	WireMockServer wireMockServer;

	@BeforeEach
	void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList().addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(SOURCE_NAME).withNamespace(NAMESPACE).build())
			.addToData(TEST_CONFIG_MAP_DEV_PROPERTIES, TEST_CONFIG_MAP_DEV_DATA)
			.addToData(TEST_CONFIG_MAP_QA_PROPERTIES, TEST_CONFIG_MAP_QA_DATA)
			.addToData(TEST_CONFIG_MAP_PROD_PROPERTIES, TEST_CONFIG_MAP_PROD_DATA)
			.addToData(TEST_CONFIG_MAP_PROPERTIES, TEST_CONFIG_MAP_DATA)
			.addToData("app.name", "test")
			.build());

		V1SecretList TEST_SECRET = new V1SecretListBuilder().withMetadata(new V1ListMetaBuilder().build())
			.addToItems(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName(SOURCE_NAME).withNamespace(NAMESPACE).build())
				.addToData("password", "p455w0rd".getBytes())
				.addToData("username", "user".getBytes())
				.build())
			.build();

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_SECRET))));
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
		WireMock.shutdownServer();
		wireMockServer.stop();
		wireMockServer.shutdownServer();

		new KubernetesClientConfigMapsCache().discardAll();
		new KubernetesClientSecretsCache().discardAll();
	}

	@Test
	void enabled() {
		Environment defaultEnv = testRestTemplate.getForObject("/test-cm/default", Environment.class);
		assertDefaultProfile(defaultEnv);

		Environment devAndProd = testRestTemplate.getForObject("/test-cm/dev,prod", Environment.class);
		assertThat(devAndProd.getPropertySources().size()).isEqualTo(4);

		assertTestConfigMapProd(devAndProd);
		assertTestConfigMapDev(devAndProd);
		assertTestConfigMapDefault(devAndProd);
		assertTestSecretDefault(devAndProd);

	}

	private void assertTestConfigMapDev(Environment devAndProd) {
		PropertySource testConfigMapDev = devAndProd.getPropertySources().get(1);
		assertThat(testConfigMapDev.getName()).isEqualTo(TEST_CONFIG_MAP_DEV_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMapDev.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.value", "1",
				"dummy.property.enabled", "false", "dummy.property.profile", "dev"));
	}

	private void assertTestConfigMapProd(Environment devAndProd) {
		PropertySource testConfigMapProd = devAndProd.getPropertySources().get(0);
		assertThat(testConfigMapProd.getName()).isEqualTo(TEST_CONFIG_MAP_PROD_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMapProd.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.value", "3",
				"dummy.property.enabled", "true", "dummy.property.profile", "prod"));
	}

	private void assertTestConfigMapDefault(Environment devAndProd) {
		PropertySource testConfigMap = devAndProd.getPropertySources().get(2);
		assertThat(testConfigMap.getName()).isEqualTo(TEST_CONFIG_MAP_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMap.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.value", "4",
				"dummy.property.enabled", "true", "dummy.property.profile", "default", "app.name", "test"));
	}

	private void assertTestSecretDefault(Environment devAndProd) {

		PropertySource testSecret = devAndProd.getPropertySources().get(3);
		assertThat(testSecret.getName()).isEqualTo(TEST_SECRET_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testSecret.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(Map.of("password", "p455w0rd", "username", "user"));
	}

	private void assertDefaultProfile(Environment defaultEnv) {
		assertThat(defaultEnv.getPropertySources().size()).isEqualTo(2);

		PropertySource configMapSource = defaultEnv.getPropertySources().get(0);
		assertThat(configMapSource.getName()).isEqualTo(TEST_CONFIG_MAP_NAME);
		@SuppressWarnings("unchecked")
		Map<String, Object> configmapData = (Map<String, Object>) configMapSource.getSource();
		assertThat(configmapData).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.value", "4",
				"dummy.property.enabled", "true", "dummy.property.profile", "default", "app.name", "test"));

		PropertySource secretSource = defaultEnv.getPropertySources().get(1);
		assertThat(secretSource.getName()).isEqualTo(TEST_SECRET_NAME);
		@SuppressWarnings("unchecked")
		Map<String, Object> secretData = (Map<String, Object>) secretSource.getSource();
		assertThat(secretData).containsExactlyInAnyOrderEntriesOf(Map.of("password", "p455w0rd", "username", "user"));
	}

}
