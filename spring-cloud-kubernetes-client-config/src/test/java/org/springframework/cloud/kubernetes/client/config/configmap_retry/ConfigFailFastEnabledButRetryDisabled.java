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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
abstract class ConfigFailFastEnabledButRetryDisabled {

	private static final String API = "/api/v1/namespaces/default/configmaps";

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
		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1ConfigMapList()))));
	}

	@AfterAll
	static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
		stubConfigMapAndSecretsDefaults();
	}

	@Autowired
	private KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	@Autowired
	private ApplicationContext context;

	@Test
	void locateShouldFailWithoutRetrying() {
		propertySourceLocator = spy(propertySourceLocator);
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		assertThat(context.containsBean("kubernetesConfigRetryInterceptor")).isFalse();
		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap(s) in namespace 'default'");

		// verify that propertySourceLocator.locate is called only once
		verify(propertySourceLocator, times(1)).locate(any());
	}

}
