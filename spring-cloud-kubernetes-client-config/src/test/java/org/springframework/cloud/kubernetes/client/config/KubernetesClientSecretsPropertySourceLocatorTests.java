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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
class KubernetesClientSecretsPropertySourceLocatorTests {

	private static final String LIST_API = "/api/v1/namespaces/default/secrets";

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

	private static final MockEnvironment ENV = new MockEnvironment();

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
	void getLocateWithSources() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API).willReturn(aResponse().withStatus(200).withBody(LIST_BODY)));

		SecretsConfigProperties.Source source1 = new SecretsConfigProperties.Source("db-secret", "",
				Collections.emptyMap(), null, null, null);

		SecretsConfigProperties.Source source2 = new SecretsConfigProperties.Source("rabbit-password", "",
				Collections.emptyMap(), null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(source1, source2), true, "app", "default", false, true, false, RetryProperties.DEFAULT);

		PropertySource<?> propertySource = new KubernetesClientSecretsPropertySourceLocator(api,
				new KubernetesNamespaceProvider(new MockEnvironment()), secretsConfigProperties).locate(ENV);
		assertThat(propertySource.containsProperty("password")).isTrue();
		assertThat(propertySource.getProperty("password")).isEqualTo("p455w0rd");
	}

	@Test
	void getLocateWithOutSources() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API).willReturn(aResponse().withStatus(200).withBody(LIST_BODY)));
		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, "db-secret", "default", false, true, false, RetryProperties.DEFAULT);

		PropertySource<?> propertySource = new KubernetesClientSecretsPropertySourceLocator(api,
				new KubernetesNamespaceProvider(new MockEnvironment()), secretsConfigProperties).locate(ENV);
		assertThat(propertySource.containsProperty("password")).isTrue();
		assertThat(propertySource.getProperty("password")).isEqualTo("p455w0rd");
	}

	/**
	 * <pre>
	 *     1. not providing the namespace
	 * </pre>
	 *
	 * will result in an Exception
	 */
	@Test
	void testLocateWithoutNamespaceConstructor() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API).willReturn(aResponse().withStatus(200).withBody(LIST_BODY)));

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, "db-secret", "", false, true, false, RetryProperties.DEFAULT);

		assertThatThrownBy(() -> new KubernetesClientSecretsPropertySourceLocator(api,
				new KubernetesNamespaceProvider(new MockEnvironment()), secretsConfigProperties).locate(ENV))
						.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, "db-secret", "default", false, true, true, RetryProperties.DEFAULT);

		KubernetesClientSecretsPropertySourceLocator locator = new KubernetesClientSecretsPropertySourceLocator(api,
				new KubernetesNamespaceProvider(new MockEnvironment()), secretsConfigProperties);

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Internal Server Error");
	}

	@Test
	void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get(LIST_API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, "db-secret", "default", false, true, false, RetryProperties.DEFAULT);

		KubernetesClientSecretsPropertySourceLocator locator = new KubernetesClientSecretsPropertySourceLocator(api,
				new KubernetesNamespaceProvider(new MockEnvironment()), secretsConfigProperties);

		assertThatNoException().isThrownBy(() -> locator.locate(new MockEnvironment()));
	}

}
