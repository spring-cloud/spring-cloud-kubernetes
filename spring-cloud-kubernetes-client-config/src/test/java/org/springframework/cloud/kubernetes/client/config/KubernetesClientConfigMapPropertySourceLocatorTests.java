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
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
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
class KubernetesClientConfigMapPropertySourceLocatorTests {

	private static final V1ConfigMapList PROPERTIES_CONFIGMAP_LIST = new V1ConfigMapList()
			.addItemsItem(
					new V1ConfigMapBuilder()
							.withMetadata(new V1ObjectMetaBuilder().withName("bootstrap-640").withNamespace("default")
									.withResourceVersion("1").build())
							.addToData("application.properties",
									"spring.cloud.kubernetes.configuration.watcher.refreshDelay=0\n"
											+ "logging.level.org.springframework.cloud.kubernetes=TRACE")
							.build());

	private static WireMockServer wireMockServer;

	private static final MockEnvironment ENV = new MockEnvironment();

	@BeforeAll
	public static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterAll
	public static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	@Test
	void locateWithoutSources() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP_LIST))));
		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "bootstrap-640", null, false, false, false, RetryProperties.DEFAULT);
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", "default");
		PropertySource<?> propertySource = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(mockEnvironment)).locate(ENV);
		assertThat(propertySource.containsProperty("spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isTrue();
	}

	@Test
	void locateWithSources() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP_LIST))));

		ConfigMapConfigProperties.Source source = new ConfigMapConfigProperties.Source("bootstrap-640", "default",
				Collections.emptyMap(), null, null, null);
		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(source), Map.of(), true, "fake-name", null, false, false, false, RetryProperties.DEFAULT);

		PropertySource<?> propertySource = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment())).locate(ENV);
		assertThat(propertySource.containsProperty("spring.cloud.kubernetes.configuration.watcher.refreshDelay"))
				.isTrue();
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
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP_LIST))));

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "bootstrap-640", null, false, false, false, RetryProperties.DEFAULT);

		assertThatThrownBy(() -> new KubernetesClientConfigMapPropertySourceLocator(api, configMapConfigProperties,
				new KubernetesNamespaceProvider(new MockEnvironment())).locate(ENV))
						.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	/**
	 * <pre>
	 *     1. not providing the namespace
	 * </pre>
	 *
	 * will result in an Exception
	 */
	@Test
	void testLocateWithoutNamespace() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(PROPERTIES_CONFIGMAP_LIST))));
		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "bootstrap-640", null, false, false, false, RetryProperties.DEFAULT);
		assertThatThrownBy(() -> new KubernetesClientConfigMapPropertySourceLocator(api, configMapConfigProperties,
				new KubernetesNamespaceProvider(ENV)).locate(ENV))
						.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	public void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "bootstrap-640", "default", false, false, true, RetryProperties.DEFAULT);

		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Internal Server Error");
	}

	@Test
	public void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "bootstrap-640", "default", false, false, false, RetryProperties.DEFAULT);

		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatNoException().isThrownBy(() -> locator.locate(new MockEnvironment()));
	}

}
