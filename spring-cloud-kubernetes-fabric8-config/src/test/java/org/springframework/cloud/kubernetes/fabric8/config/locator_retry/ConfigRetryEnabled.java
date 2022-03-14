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

package org.springframework.cloud.kubernetes.fabric8.config.locator_retry;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.fabric8.config.Application;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.config.retry.max-attempts=5",
				"spring.main.cloud-platform=KUBERNETES" },
		classes = Application.class)
@EnableKubernetesMockClient
class ConfigRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/configmaps/application";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setup() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@SpyBean
	private Fabric8ConfigMapPropertySourceLocator propertySourceLocator;

	@Test
	void locateShouldNotRetryWhenThereIsNoFailure() {
		Map<String, String> data = new HashMap<>();
		data.put("some.prop", "theValue");
		data.put("some.number", "0");

		// return config map without failing
		mockServer.expect().withPath(API).andReturn(200,
				new ConfigMapBuilder().withNewMetadata().withName("application").endMetadata().addToData(data).build())
				.once();

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

		// fail 3 times then succeed at the 4th call
		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").times(3);
		mockServer.expect().withPath(API).andReturn(200,
				new ConfigMapBuilder().withNewMetadata().withName("application").endMetadata().addToData(data).build())
				.once();

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
		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").times(5);

		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap with name 'application' in namespace 'default'");

		// verify retried 5 times until failure
		verify(propertySourceLocator, times(5)).locate(any());
	}

}
