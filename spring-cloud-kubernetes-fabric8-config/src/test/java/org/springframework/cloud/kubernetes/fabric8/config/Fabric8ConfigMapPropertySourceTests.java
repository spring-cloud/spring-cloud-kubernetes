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

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
class Fabric8ConfigMapPropertySourceTests {

	private KubernetesMockServer mockServer;

	private KubernetesClient mockClient;

	private final DefaultKubernetesClient client = Mockito.mock(DefaultKubernetesClient.class);

	@Test
	void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/configmaps/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(mockClient, name, namespace, new MockEnvironment(),
				"", false, true)).isInstanceOf(IllegalStateException.class).hasMessage(
						"Unable to read ConfigMap with name '" + name + "' in namespace '" + namespace + "'");
	}

	@Test
	void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/configmaps/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatNoException().isThrownBy(() -> new Fabric8ConfigMapPropertySource(mockClient, name, namespace,
				new MockEnvironment(), "", false, false));
	}

	@Test
	void constructorWithoutClientNamespaceMustFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment(),
				"", false, false)).isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void constructorWithClientNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn("namespace");
		assertThat(
				new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment(), "", false, false))
						.isNotNull();
	}

	@Test
	void constructorWithNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap", "namespace", new MockEnvironment(), "",
				false, false)).isNotNull();
	}

}
