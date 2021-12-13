/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
class KubernetesClientSecretsPropertySourceTests {

	private static final String API = "/api/v1/namespaces/default/secrets";

	private static final V1SecretList SECRET_LIST = new V1SecretListBuilder().addToItems(new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("db-secret").withResourceVersion("0")
					.withNamespace("default").build())
			.addToData("password", "p455w0rd".getBytes()).addToData("username", "user".getBytes()).build()).build();

	private static final String LIST_API = "/api/v1/secrets";

	private static final String LIST_API_WITH_LABEL = "/api/v1/namespaces/default/secrets?labelSelector=spring.cloud.kubernetes.secret%3Dtrue";

	private static final String LIST_BODY = "{\n" + "\t\"kind\": \"SecretList\",\n" + "\t\"apiVersion\": \"v1\",\n"
			+ "\t\"metadata\": {\n" + "\t\t\"selfLink\": \"/api/v1/secrets\",\n"
			+ "\t\t\"resourceVersion\": \"163035\"\n" + "\t},\n" + "\t\"items\": [{\n" + "\t\t\t\"metadata\": {\n"
			+ "\t\t\t\t\"name\": \"db-secret\",\n" + "\t\t\t\t\"namespace\": \"default\",\n"
			+ "\t\t\t\t\"selfLink\": \"/api/v1/namespaces/default/secrets/db-secret\",\n"
			+ "\t\t\t\t\"uid\": \"59ba8e6a-a2d4-416c-b016-22597c193f23\",\n"
			+ "\t\t\t\t\"resourceVersion\": \"1462\",\n" + "\t\t\t\t\"creationTimestamp\": \"2020-10-28T14:45:02Z\",\n"
			+ "\t\t\t\t\"labels\": {\n" + "\t\t\t\t\t\"spring.cloud.kubernetes.secret\": \"true\"\n" + "\t\t\t\t}\n"
			+ "\t\t\t},\n" + "\t\t\t\"data\": {\n" + "\t\t\t\t\"password\": \"cDQ1NXcwcmQ=\",\n"
			+ "\t\t\t\t\"username\": \"dXNlcg==\"\n" + "\t\t\t},\n" + "\t\t\t\"type\": \"Opaque\"\n" + "\t\t},\n"
			+ "\t\t{\n" + "\t\t\t\"metadata\": {\n" + "\t\t\t\t\"name\": \"rabbit-password\",\n"
			+ "\t\t\t\t\"namespace\": \"default\",\n"
			+ "\t\t\t\t\"selfLink\": \"/api/v1/namespaces/default/secrets/rabbit-password\",\n"
			+ "\t\t\t\t\"uid\": \"bc211cb4-e7ff-4556-b26e-c54911301740\",\n"
			+ "\t\t\t\t\"resourceVersion\": \"162708\",\n"
			+ "\t\t\t\t\"creationTimestamp\": \"2020-10-29T19:47:36Z\",\n" + "\t\t\t\t\"labels\": {\n"
			+ "\t\t\t\t\t\"spring.cloud.kubernetes.secret\": \"true\"\n" + "\t\t\t\t},\n"
			+ "\t\t\t\t\"annotations\": {\n"
			+ "\t\t\t\t\t\"kubectl.kubernetes.io/last-applied-configuration\": \"{\\\"apiVersion\\\":\\\"v1\\\",\\\"data\\\":{\\\"spring.rabbitmq.password\\\":\\\"password\\\"},\\\"kind\\\":\\\"Secret\\\",\\\"metadata\\\":{\\\"annotations\\\":{},\\\"labels\\\":{\\\"spring.cloud.kubernetes.secret\\\":\\\"true\\\"},\\\"name\\\":\\\"rabbit-password\\\",\\\"namespace\\\":\\\"default\\\"},\\\"type\\\":\\\"Opaque\\\"}\\n\"\n"
			+ "\t\t\t\t}\n" + "\t\t\t},\n" + "\t\t\t\"data\": {\n"
			+ "\t\t\t\t\"spring.rabbitmq.password\": \"cGFzc3dvcmQ=\"\n" + "\t\t\t},\n" + "\t\t\t\"type\": \"Opaque\"\n"
			+ "\t\t}\n" + "\t]\n" + "}";

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@Test
	void secretsTest() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRET_LIST))));
		KubernetesClientSecretsPropertySource propertySource = new KubernetesClientSecretsPropertySource(api,
				"db-secret", "default", new HashMap<>(), false);
		assertThat(propertySource.containsProperty("password")).isTrue();
		assertThat(propertySource.getProperty("password")).isEqualTo("p455w0rd");
		assertThat(propertySource.containsProperty("username")).isTrue();
		assertThat(propertySource.getProperty("username")).isEqualTo("user");
	}

	@Test
	void secretLabelsTest() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API_WITH_LABEL).willReturn(aResponse().withStatus(200).withBody(LIST_BODY)));
		Map<String, String> labels = new HashMap<>();
		labels.put("spring.cloud.kubernetes.secret", "true");
		KubernetesClientSecretsPropertySource propertySource = new KubernetesClientSecretsPropertySource(api, null,
				"default", labels, false);
		assertThat(propertySource.containsProperty("spring.rabbitmq.password")).isTrue();
		assertThat(propertySource.getProperty("spring.rabbitmq.password")).isEqualTo("password");
	}

	@Test
	void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		assertThatThrownBy(() -> new KubernetesClientSecretsPropertySource(api, "db-secret", "default", null, true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name 'db-secret' or labels [null] in namespace 'default'");
		verify(getRequestedFor(urlEqualTo(API)));
	}

	@Test
	void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		assertThatNoException().isThrownBy(
				(() -> new KubernetesClientSecretsPropertySource(api, "db-secret", "default", null, false)));
		verify(getRequestedFor(urlEqualTo(API)));
	}

	@Test
	void constructorMustFailWhenNamespaceIsNoProvided() {
		CoreV1Api api = new CoreV1Api();
		assertThatThrownBy((() -> new KubernetesClientSecretsPropertySource(api, "db-secret", null, null, false)))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

}
