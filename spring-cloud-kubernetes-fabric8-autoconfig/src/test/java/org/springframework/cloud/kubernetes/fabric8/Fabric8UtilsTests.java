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

package org.springframework.cloud.kubernetes.fabric8;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8UtilsTests {

	private KubernetesClient client;

	private final KubernetesNamespaceProvider provider = Mockito.mock(KubernetesNamespaceProvider.class);

	@Test
	void testGetApplicationNamespaceNotPresent() {
		String result = Fabric8Utils.getApplicationNamespace(client, "", "target", null);
		assertThat(result).isEqualTo("test");
	}

	@Test
	void testGetApplicationNamespacePresent() {
		String result = Fabric8Utils.getApplicationNamespace(client, "namespace", "target", null);
		assertThat(result).isEqualTo("namespace");
	}

	@Test
	void testNamespaceFromNormalizedSource() {
		String result = Fabric8Utils.getApplicationNamespace(client, "abc", "target", null);
		assertThat(result).isEqualTo("abc");
	}

	@Test
	void testNamespaceFromProvider() {
		Mockito.when(provider.getNamespace()).thenReturn("def");
		String result = Fabric8Utils.getApplicationNamespace(client, "", "target", provider);
		assertThat(result).isEqualTo("def");
	}

}
