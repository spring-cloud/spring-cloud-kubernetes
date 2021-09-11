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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
public class Fabric8ConfigUtilsTests {

	private KubernetesClient client;

	private final DefaultKubernetesClient mockClient = Mockito.mock(DefaultKubernetesClient.class);

	private final KubernetesNamespaceProvider provider = Mockito.mock(KubernetesNamespaceProvider.class);

	@Test
	public void testGetApplicationNamespaceNotPresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "", "target");
		assertThat(result).isEqualTo("test");
	}

	@Test
	public void testGetApplicationNamespacePresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "namespace", "target");
		assertThat(result).isEqualTo("namespace");
	}

	@Test
	void testNamespaceFromNormalizedSource() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "abc", "target", null);
		assertThat(result).isEqualTo("abc");
	}

	@Test
	void testNamespaceFromProvider() {
		Mockito.when(provider.getNamespace()).thenReturn("def");
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "", "target", provider);
		assertThat(result).isEqualTo("def");
	}

	@Test
	void testNamespaceFromClient() {
		Mockito.when(mockClient.getNamespace()).thenReturn("qwe");
		String result = Fabric8ConfigUtils.getApplicationNamespace(mockClient, "", "target", null);
		assertThat(result).isEqualTo("qwe");
	}

	@Test
	void testNamespaceResolutionFailed() {
		assertThatThrownBy(() -> Fabric8ConfigUtils.getApplicationNamespace(mockClient, "", "target", null))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

}
