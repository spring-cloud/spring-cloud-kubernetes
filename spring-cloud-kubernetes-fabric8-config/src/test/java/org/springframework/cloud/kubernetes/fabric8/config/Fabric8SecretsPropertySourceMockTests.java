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

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * tests that are supposed to use EnableKubernetesMockClient only
 *
 * @author wind57
 */
@EnableKubernetesMockClient
class Fabric8SecretsPropertySourceMockTests {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient client;

	private final DefaultKubernetesClient mockClient = Mockito.mock(DefaultKubernetesClient.class);

	@Test
	void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatThrownBy(() -> new Fabric8SecretsPropertySource(client, name, namespace,
			Collections.emptyMap(), true)).isInstanceOf(IllegalStateException.class)
			.hasMessage("Unable to read Secret with name '" + name + "' or labels [{}] in namespace '"
				+ namespace + "'");
	}

	@Test
	void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatNoException().isThrownBy(() -> new Fabric8SecretsPropertySource(client, name,
			namespace, Collections.emptyMap(), false));
	}

	@Test
	void constructorWithoutClientNamespaceMustFail() {
		Mockito.when(mockClient.getNamespace()).thenReturn(null);
		assertThatThrownBy(() -> new Fabric8SecretsPropertySource(mockClient, "my-secret", null, Collections.emptyMap(), false))
			.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void constructorWithClientNamespaceMustNotFail() {
		Mockito.when(mockClient.getNamespace()).thenReturn("namespace");
		assertThat(new Fabric8SecretsPropertySource(mockClient, "my-secret", null, Collections.emptyMap(), false)).isNotNull();
	}

	@Test
	void constructorWithNamespaceMustNotFail() {
		Mockito.when(mockClient.getNamespace()).thenReturn(null);
		assertThat(new Fabric8SecretsPropertySource(mockClient, "my-secret", "ns", Collections.emptyMap(), false))
			.isNotNull();
	}

}
