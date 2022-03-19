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

package org.springframework.cloud.kubernetes.client.config.configmap_retry;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.RetryableKubernetesClientConfigMapPropertySourceLocator;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.config.retry.max-attempts=5",
				"spring.main.cloud-platform=KUBERNETES" },
		classes = App.class)
class ConfigRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/configmaps/application";

	private static WireMockServer wireMockServer;

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::kubernetesApiClient)
				.thenReturn(new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build());
	}

	@AfterAll
	static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@SpyBean
	private RetryableKubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	@Test
	void locateShouldNotRetryWhenThereIsNoFailure() {

		Map<String, String> data = new HashMap<>();
		data.put("some.prop", "theValue");
		data.put("some.number", "0");

		V1ConfigMap configMap = new V1ConfigMap().metadata(new V1ObjectMeta().name("application")).data(data);

		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMap))));

		PropertySource<?> propertySource = Assertions
				.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

		// verify locate is called only once
		verify(propertySourceLocator, times(1)).locate(any());

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.prop")).isEqualTo("theValue");
		assertThat(propertySource.getProperty("some.number")).isEqualTo("0");
	}

	@Test
	void locateShouldRetryAndRecover() {
		Map<String, String> data = new HashMap<>();
		data.put("some.prop", "theValue");
		data.put("some.number", "0");

		V1ConfigMap configMap = new V1ConfigMap().metadata(new V1ObjectMeta().name("application")).data(data);

		// fail 3 times
		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs(STARTED)
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed once"));

		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed once")
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed twice"));

		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed twice")
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed thrice"));

		// then succeed
		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed thrice")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMap))));

		PropertySource<?> propertySource = Assertions
				.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

		// verify retried 4 times
		verify(propertySourceLocator, times(4)).locate(any());

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.prop")).isEqualTo("theValue");
		assertThat(propertySource.getProperty("some.number")).isEqualTo("0");
	}

	@Test
	void locateShouldRetryAndFail() {
		// fail all the 5 requests
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap with name 'application' in namespace 'default'");

		// verify retried 5 times until failure
		verify(propertySourceLocator, times(5)).locate(any());
	}

}
