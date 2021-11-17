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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
public class Fabric8SecretsPropertySourceLocatorTests {

	KubernetesMockServer mockServer;

	KubernetesClient mockClient;

	@Test
	public void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties();
		configMapConfigProperties.setName(name);
		configMapConfigProperties.setNamespace(namespace);
		configMapConfigProperties.setEnableApi(true);
		configMapConfigProperties.setFailFast(true);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'");
	}

	@Test
	public void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties();
		configMapConfigProperties.setName(name);
		configMapConfigProperties.setNamespace(namespace);
		configMapConfigProperties.setEnableApi(true);
		configMapConfigProperties.setFailFast(false);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatNoException().isThrownBy(() -> locator.locate(new MockEnvironment()));
	}

}
