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

package org.springframework.cloud.kubernetes.fabric8.config.retry;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
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
abstract class SecretsRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/secrets/my-secret";

	private static final String LIST_API = "/api/v1/namespaces/default/secrets";

	private static KubernetesMockServer mockServer;

	protected SecretsPropertySourceLocator psl;

	protected SecretsPropertySourceLocator verifiablePsl;

	static void setup(KubernetesClient mockClient, KubernetesMockServer mockServer) {
		SecretsRetryEnabled.mockServer = mockServer;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// return empty secret list to not fail context creation
		mockServer.expect().withPath(LIST_API).andReturn(200, new SecretListBuilder().build()).always();
	}

	@Test
	void locateShouldNotRetryWhenThereIsNoFailure() {
		Map<String, String> data = new HashMap<>();
		data.put("some.sensitive.prop", Base64.getEncoder().encodeToString("theSensitiveValue".getBytes()));
		data.put("some.sensitive.number", Base64.getEncoder().encodeToString("1".getBytes()));

		// return secret without failing
		mockServer.expect().withPath(API).andReturn(200,
				new SecretBuilder().withNewMetadata().withName("my-secret").endMetadata().addToData(data).build())
				.once();

		PropertySource<?> propertySource = Assertions.assertDoesNotThrow(() -> psl.locate(new MockEnvironment()));

		// verify locate is called only once
		verify(verifiablePsl, times(1)).locate(any());

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
		assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
	}

	@Test
	void locateShouldRetryAndRecover() {
		Map<String, String> data = new HashMap<>();
		data.put("some.sensitive.prop", Base64.getEncoder().encodeToString("theSensitiveValue".getBytes()));
		data.put("some.sensitive.number", Base64.getEncoder().encodeToString("1".getBytes()));

		// fail 3 times then succeed at the 4th call
		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").times(3);
		mockServer.expect().withPath(API).andReturn(200,
				new SecretBuilder().withNewMetadata().withName("my-secret").endMetadata().addToData(data).build())
				.once();

		PropertySource<?> propertySource = Assertions.assertDoesNotThrow(() -> psl.locate(new MockEnvironment()));

		// verify retried 4 times
		verify(verifiablePsl, times(4)).locate(any());

		// validate the contents of the property source
		assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
		assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
	}

	@Test
	void locateShouldRetryAndFail() {
		// fail all the 5 requests
		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").times(5);

		assertThatThrownBy(() -> psl.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name 'my-secret' in namespace 'default'");

		// verify retried 5 times until failure
		verify(verifiablePsl, times(5)).locate(any());

	}

}
