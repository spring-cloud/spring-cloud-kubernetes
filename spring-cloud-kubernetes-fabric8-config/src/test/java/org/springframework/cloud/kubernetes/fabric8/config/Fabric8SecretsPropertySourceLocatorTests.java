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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
class Fabric8SecretsPropertySourceLocatorTests {

	KubernetesMockServer mockServer;

	KubernetesClient mockClient;

	@Test
	void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		String name = "my-secret";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, name, namespace, false, true, true, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("v1/namespaces/default/secrets. Message: Internal Server Error.");
	}

	@Test
	void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		String name = "my-secret";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets/my-secret";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, name, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatNoException().isThrownBy(() -> locator.locate(new MockEnvironment()));
	}

}
