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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.mock.env.MockEnvironment;

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
class KubernetesClientConfigMapPropertySourceTests {

	private static final V1ConfigMap PROPERTIES_CONFIGMAP = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("bootstrap-640").withNamespace("default")
					.withResourceVersion("1").build())
			.addToData("application.properties", "spring.cloud.kubernetes.configuration.watcher.refreshDelay=0\n"
					+ "logging.level.org.springframework.cloud.kubernetes=TRACE")
			.build();

	private static final V1ConfigMap YAML_CONFIGMAP = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("bootstrap-641").withNamespace("default")
					.withResourceVersion("1").build())
			.addToData("application.yaml", "dummy:\n  property:\n    string2: \"a\"\n    int2: 1\n    bool2: true\n")
			.build();

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
	void propertiesFile() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps/bootstrap-640")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP))));

		NormalizedSource source = new NamedConfigMapNormalizedSource("bootstrap-640", "default", false, "", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, "default",
				new MockEnvironment());
		KubernetesClientConfigMapPropertySource propertySource = new KubernetesClientConfigMapPropertySource(context);

		verify(getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps/bootstrap-640")));
		assertThat(propertySource.containsProperty("spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isTrue();
		assertThat(propertySource.getProperty("spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isEqualTo("0");
		assertThat(propertySource.containsProperty("logging.level.org.springframework.cloud.kubernetes")).isTrue();
		assertThat(propertySource.getProperty("logging.level.org.springframework.cloud.kubernetes")).isEqualTo("TRACE");

	}

	@Test
	void yamlFile() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps/bootstrap-641")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(YAML_CONFIGMAP))));

		NormalizedSource source = new NamedConfigMapNormalizedSource("bootstrap-641", "default", false, "", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, "default",
				new MockEnvironment());
		KubernetesClientConfigMapPropertySource propertySource = new KubernetesClientConfigMapPropertySource(context);

		verify(getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps/bootstrap-641")));
		assertThat(propertySource.containsProperty("dummy.property.string2")).isTrue();
		assertThat(propertySource.getProperty("dummy.property.string2")).isEqualTo("a");
		assertThat(propertySource.containsProperty("dummy.property.int2")).isTrue();
		assertThat(propertySource.getProperty("dummy.property.int2")).isEqualTo(1);
		assertThat(propertySource.containsProperty("dummy.property.bool2")).isTrue();
		assertThat(propertySource.getProperty("dummy.property.bool2")).isEqualTo(true);

	}

	@Test
	void propertiesFileWithPrefix() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps/bootstrap-640")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP))));

		NormalizedSource source = new NamedConfigMapNormalizedSource("bootstrap-640", "default", false, "prefix", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, "default",
				new MockEnvironment());
		KubernetesClientConfigMapPropertySource propertySource = new KubernetesClientConfigMapPropertySource(context);

		verify(getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps/bootstrap-640")));
		assertThat(propertySource.containsProperty("prefix.spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isTrue();
		assertThat(propertySource.getProperty("prefix.spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isEqualTo("0");
		assertThat(propertySource.containsProperty("prefix.logging.level.org.springframework.cloud.kubernetes"))
				.isTrue();
		assertThat(propertySource.getProperty("prefix.logging.level.org.springframework.cloud.kubernetes"))
				.isEqualTo("TRACE");
	}

	@Test
	void constructorWithNamespaceMustNotFail() {

		NormalizedSource source = new NamedConfigMapNormalizedSource("bootstrap-640", "default", false, "prefix", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(new CoreV1Api(), source, "default",
				new MockEnvironment());

		assertThat(new KubernetesClientConfigMapPropertySource(context)).isNotNull();
	}

	@Test
	void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		stubFor(get("/api/v1/namespaces/default/configmaps/my-config")
				.willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		NormalizedSource source = new NamedConfigMapNormalizedSource("my-config", "default", true, "prefix", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(new CoreV1Api(), source, "default",
				new MockEnvironment());

		assertThatThrownBy(() -> new KubernetesClientConfigMapPropertySource(context))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap with name 'my-config' in namespace 'default'");
		verify(getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps/my-config")));
	}

	@Test
	void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		stubFor(get("/api/v1/namespaces/default/configmaps/my-config")
				.willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		NormalizedSource source = new NamedConfigMapNormalizedSource("my-config", "default", false, "prefix", true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(new CoreV1Api(), source, "default",
				new MockEnvironment());

		assertThatNoException().isThrownBy((() -> new KubernetesClientConfigMapPropertySource(context)));
		verify(getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps/my-config")));
	}

}
