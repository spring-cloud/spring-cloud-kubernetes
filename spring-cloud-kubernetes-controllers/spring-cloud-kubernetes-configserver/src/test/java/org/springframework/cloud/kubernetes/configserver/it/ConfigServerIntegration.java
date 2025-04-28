/*
 * Copyright 2013-2024 the original author or authors.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
abstract class ConfigServerIntegration {

	private static final String TEST_CONFIG_MAP_DEV_YAML = "test-cm-dev.yaml";
	private static final String TEST_CONFIG_MAP_DEV_NAME = "configmap.test-cm.default.dev";
	private static final String TEST_CONFIG_MAP_DEV_DATA = """
         dummy:
           property:
             profile: dev
             value: 1
             enabled: false
    """;

	private static final String TEST_CONFIG_MAP_QA_YAML = "test-cm-qa.yaml";
	private static final String TEST_CONFIG_MAP_QA_DATA = """
         dummy:
           property:
             profile: qa
             value: 2
             enabled: true
    """;

	private static final String TEST_CONFIG_MAP_PROD_YAML = "test-cm-prod.yaml";
	private static final String TEST_CONFIG_MAP_PROD_NAME = "configmap.test-cm.default.prod";
	private static final String TEST_CONFIG_MAP_PROD_DATA = """
         dummy:
           property:
             profile: prod
             value: 3
             enabled: true
    """;

	private static final String TEST_CONFIG_MAP_YAML = "test-cm.yaml";
	private static final String TEST_CONFIG_MAP_NAME = "configmap.test-cm.default.default";
	private static final String TEST_CONFIG_MAP_DATA = """
         dummy:
           property:
             profile: default
             value: 4
             enabled: true
    """;

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	WireMockServer wireMockServer;

	@BeforeEach
	void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList().addItemsItem(new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName("test-cm").withNamespace("default").build())
			.addToData(TEST_CONFIG_MAP_DEV_YAML, TEST_CONFIG_MAP_DEV_DATA)
			.addToData(TEST_CONFIG_MAP_QA_YAML, TEST_CONFIG_MAP_QA_DATA)
			.addToData(TEST_CONFIG_MAP_PROD_YAML, TEST_CONFIG_MAP_PROD_DATA)
			.addToData(TEST_CONFIG_MAP_YAML, TEST_CONFIG_MAP_DATA)
			.addToData("app.name", "test")
			.build());

		V1SecretList TEST_SECRET = new V1SecretListBuilder()
			.withMetadata(new V1ListMetaBuilder().build())
			.addToItems(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("test-cm")
					.withResourceVersion("0")
					.withNamespace("default")
					.build())
				.addToData("password", "p455w0rd".getBytes())
				.addToData("username", "user".getBytes())
				.build())
			.build();

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_SECRET))));
	}

	@Test
	void enabled() {
		Environment env = testRestTemplate.getForObject("/test-cm/default", Environment.class);
		assertThat(env.getPropertySources().size()).isEqualTo(2);
		assertThat(env.getPropertySources().get(0).getName().equals("configmap.test-cm.default.default")).isTrue();
		assertThat(env.getPropertySources().get(0).getSource().get("app.name")).isEqualTo("test");
		assertThat(env.getPropertySources().get(1).getName().equals("secret.test-cm.default.default")).isTrue();
		assertThat(env.getPropertySources().get(1).getSource().get("password")).isEqualTo("p455w0rd");
		assertThat(env.getPropertySources().get(1).getSource().get("username")).isEqualTo("user");

		Environment devprod = testRestTemplate.getForObject("/test-cm/dev,prod", Environment.class);
		assertThat(devprod.getPropertySources().size()).isEqualTo(4);

		assertTestConfigMapProd(devprod);
		assertTestConfigMapDev(devprod);
		assertTestConfigMapDefault(devprod);


		assertThat(devprod.getPropertySources().get(3).getName().equals("secret.test-cm.default.default")).isTrue();
		assertThat(devprod.getPropertySources().get(3).getSource().size()).isEqualTo(2);
		assertThat(devprod.getPropertySources().get(3).getSource().get("password")).isEqualTo("p455w0rd");
		assertThat(devprod.getPropertySources().get(3).getSource().get("username")).isEqualTo("user");
	}

	private void assertTestConfigMapDev(Environment devAndProd) {
		PropertySource testConfigMapDev = devAndProd.getPropertySources().get(1);
		assertThat(testConfigMapDev.getName()).isEqualTo(TEST_CONFIG_MAP_DEV_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMapDev.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(
			Map.of("dummy.property.value", 1, "dummy.property.enabled", false, "dummy.property.profile", "dev"));
	}

	private void assertTestConfigMapProd(Environment devAndProd) {
		PropertySource testConfigMapDev = devAndProd.getPropertySources().get(0);
		assertThat(testConfigMapDev.getName()).isEqualTo(TEST_CONFIG_MAP_PROD_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMapDev.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(
			Map.of("dummy.property.value", 3, "dummy.property.enabled", true, "dummy.property.profile", "prod"));
	}

	private void assertTestConfigMapDefault(Environment devAndProd) {
		PropertySource testConfigMapDev = devAndProd.getPropertySources().get(2);
		assertThat(testConfigMapDev.getName()).isEqualTo(TEST_CONFIG_MAP_NAME);

		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>) testConfigMapDev.getSource();
		assertThat(data).containsExactlyInAnyOrderEntriesOf(
			Map.of("dummy.property.value", 4, "dummy.property.enabled", true, "dummy.property.profile", "default"));
	}

}
