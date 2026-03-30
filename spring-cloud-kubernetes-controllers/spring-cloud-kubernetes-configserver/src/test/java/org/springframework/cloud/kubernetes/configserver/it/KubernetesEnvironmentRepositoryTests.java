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
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.kubernetes.configserver.KubernetesConfigServerApplication;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.kubernetes.client.openapi.JSON.serialize;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesEnvironmentRepositoryTests {

	private static final V1ConfigMapList CONFIGMAP_LIST_DEFAULT_NAMESPACE =
		Util.yaml("configmap-default-list.yaml", V1ConfigMapList.class);

	private static final V1ConfigMapList CONFIGMAP_LIST_DEV_NAMESPACE =
		Util.yaml("configmap-dev-list.yaml", V1ConfigMapList.class);

	private static final V1SecretList SECRET_LIST_DEFAULT_NAMESPACE =
		Util.yaml("secret-one-list.yaml", V1SecretList.class);

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void beforeAll() {

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();

		wireMockServer.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(CONFIGMAP_LIST_DEFAULT_NAMESPACE))));

		wireMockServer.stubFor(get(urlMatching("^/api/v1/namespaces/dev/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(CONFIGMAP_LIST_DEV_NAMESPACE))));

		wireMockServer.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(serialize(SECRET_LIST_DEFAULT_NAMESPACE))));
	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
		wireMockServer.shutdownServer();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		ApiClient apiClient() {
			return new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
		}

	}

	/**
	 * <pre>
	 * Given application=application and an empty profile,
	 * the repository loads:
	 * - the application ConfigMap
	 * - the application Secret
	 * </pre>
	 */
	@Nested
	@AutoConfigureTestRestTemplate
	@SpringBootTest(
		properties = { "spring.cloud.kubernetes.secrets.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
			"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = { KubernetesConfigServerApplication.class, TestConfig.class })
	@DirtiesContext
	class ApplicationCaseTest {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		@SuppressWarnings("unchecked")
		void test() {
			Environment environment = testRestTemplate.getForObject("/application/default ", Environment.class);

			Map<String, Map<String, Object>> result = environment.getPropertySources()
				.stream()
				.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

			assertThat(result.keySet()).containsExactly("secret.application.default", "configmap.application.default");

			Map<String, Object> fromConfigMap = result.get("configmap.application.default");
			Map<String, Object> fromSecret = result.get("secret.application.default");

			assertThat(fromConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

			assertThat(fromSecret).containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));
		}
	}

	/**
	 * <pre>
	 * Given application=application and an empty profile,
	 * the repository loads:
	 * - the application ConfigMap
	 * - the application Secret
	 * and preserves the configured repository order.
	 * </pre>
	 */
	@Nested
	@AutoConfigureTestRestTemplate
	@SpringBootTest(
		properties = { "spring.cloud.kubernetes.secrets.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
			"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = { KubernetesConfigServerApplication.class, TestConfig.class })
	@DirtiesContext
	class StoresCaseTest {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		@SuppressWarnings("unchecked")
		void test() {

			Environment environment = testRestTemplate.getForObject("/stores/default ", Environment.class);

			Map<String, Map<String, Object>> result = environment.getPropertySources()
				.stream()
				.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

			assertThat(result.keySet()).containsExactly("configmap.stores.default", "secret.application.default",
				"configmap.application.default", "secret.stores.default");

			Map<String, Object> fromApplicationConfigMap = result.get("configmap.application.default");
			Map<String, Object> fromApplicationSecret = result.get("secret.application.default");
			Map<String, Object> fromStoresConfigMap = result.get("configmap.stores.default");
			Map<String, Object> fromStoresSecret = result.get("secret.stores.default");

			assertThat(fromApplicationConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

			assertThat(fromApplicationSecret)
				.containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));

			assertThat(fromStoresConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

			assertThat(fromStoresSecret)
				.containsExactlyInAnyOrderEntriesOf(Map.of("username", "stores", "password", "password-from-stores"));

		}
	}

}
