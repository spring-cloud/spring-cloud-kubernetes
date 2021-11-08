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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * a few tests around namespace resolution
 *
 * @author wind57
 */
class Fabric8ConfigMapPropertySourceTests {

	private final KubernetesClient client = Mockito.mock(KubernetesClient.class);

	@Test
	void deprecatedConstructorWithoutClientNamespaceMustFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(client, "configmap"))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void deprecatedConstructorWithClientNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn("some");
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap")).isNotNull();
	}

	@Test
	void anotherDeprecatedConstructorWithoutClientNamespaceMustFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment()))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void anotherDeprecatedConstructorWithClientNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn("some-namespace");
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment())).isNotNull();
	}

	@Test
	void anotherDeprecatedConstructorWithNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap", "namespace", new MockEnvironment()))
				.isNotNull();
	}

	@Test
	void constructorWithoutClientNamespaceMustFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment()))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void constructorWithClientNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn("namespace");
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap", null, new MockEnvironment())).isNotNull();
	}

	@Test
	void constructorWithNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		assertThat(new Fabric8ConfigMapPropertySource(client, "configmap", "namespace", new MockEnvironment()))
				.isNotNull();
	}

}
