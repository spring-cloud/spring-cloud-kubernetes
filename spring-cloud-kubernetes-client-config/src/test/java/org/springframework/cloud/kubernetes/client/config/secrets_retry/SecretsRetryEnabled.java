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

package org.springframework.cloud.kubernetes.client.config.secrets_retry;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.secrets.fail-fast=true",
				"spring.cloud.kubernetes.secrets.retry.max-attempts=5",
				"spring.cloud.kubernetes.secrets.name=my-secret", "spring.cloud.kubernetes.secrets.enable-api=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.config.import=kubernetes:" },
		classes = Application.class)
class SecretsRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/secrets";

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
		stubConfigMapAndSecretsDefaults();
	}

	private static void stubConfigMapAndSecretsDefaults() {
		// return empty config map / secret list to not fail context creation
		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1SecretList()))));
	}

	@AfterAll
	static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	@Autowired
	private SecretsPropertySourceLocator psl;

	@BeforeEach
	void afterEach() {
		WireMock.reset();
		stubConfigMapAndSecretsDefaults();
	}

	@Test
	void locateShouldNotRetryWhenThereIsNoFailure() {
		SecretsPropertySourceLocator propertySourceLocator = spy(psl);
		Map<String, byte[]> data = new HashMap<>();
		data.put("some.sensitive.prop", "theSensitiveValue".getBytes());
		data.put("some.sensitive.number", "1".getBytes());

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("my-secret")).data(data));

		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		PropertySource<?> propertySource = Assertions
				.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

		// verify locate is called only once
		WireMock.verify(1, getRequestedFor(urlEqualTo(API)));

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
		assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
	}

	@Test
	void locateShouldRetryAndRecover() {
		SecretsPropertySourceLocator propertySourceLocator = spy(psl);
		Map<String, byte[]> data = new HashMap<>();
		data.put("some.sensitive.prop", "theSensitiveValue".getBytes());
		data.put("some.sensitive.number", "1".getBytes());

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("my-secret")).data(data));

		// fail 3 times
		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs(STARTED)
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed once"));

		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed once")
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed twice"));

		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed twice")
				.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed thrice"));

		// then succeed
		stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed thrice")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		PropertySource<?> propertySource = Assertions
				.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

		// verify retried 4 times
		WireMock.verify(4, getRequestedFor(urlEqualTo(API)));

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
		assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
	}

	@Test
	void locateShouldRetryAndFail() {
		SecretsPropertySourceLocator propertySourceLocator = spy(psl);
		// fail all the 5 requests
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name 'my-secret' in namespace 'default'");

		// verify retried 5 times until failure
		WireMock.verify(5, getRequestedFor(urlEqualTo(API)));
	}

}
