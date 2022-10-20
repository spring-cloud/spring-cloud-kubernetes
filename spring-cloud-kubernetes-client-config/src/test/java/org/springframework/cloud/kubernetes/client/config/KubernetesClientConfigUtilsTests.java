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

package org.springframework.cloud.kubernetes.client.config;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author wind57
 */
class KubernetesClientConfigUtilsTests {

	private final KubernetesNamespaceProvider provider = Mockito.mock(KubernetesNamespaceProvider.class);

	@Test
	void testNamespaceFromNormalizedSource() {
		String result = KubernetesClientConfigUtils.getApplicationNamespace("abc", "target", null);
		assertThat(result).isEqualTo("abc");
	}

	@Test
	void testNamespaceFromProvider() {
		Mockito.when(provider.getNamespace()).thenReturn("def");
		String result = KubernetesClientConfigUtils.getApplicationNamespace("", "target", provider);
		assertThat(result).isEqualTo("def");
	}

	@Test
	void testNamespaceResolutionFailed() {
		assertThatThrownBy(() -> KubernetesClientConfigUtils.getApplicationNamespace("", "target", null))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	@Test
	void testNamespacesFromProperties() {
		ConfigReloadProperties properties = new ConfigReloadProperties(false, false, false,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
				Duration.ofMillis(15000), Set.of("non-default"), false, Duration.ofSeconds(2));
		Set<String> namespaces = KubernetesClientConfigUtils
				.namespaces(new KubernetesNamespaceProvider(new MockEnvironment()), properties, "configmap");
		Assertions.assertEquals(1, namespaces.size());
		Assertions.assertEquals(namespaces.iterator().next(), "non-default");
	}

	@Test
	void testNamespacesFromProvider() {
		ConfigReloadProperties properties = ConfigReloadProperties.DEFAULT;
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.client.namespace", "some");
		KubernetesNamespaceProvider provider = new KubernetesNamespaceProvider(environment);
		Set<String> namespaces = KubernetesClientConfigUtils.namespaces(provider, properties, "configmap");
		Assertions.assertEquals(1, namespaces.size());
		Assertions.assertEquals(namespaces.iterator().next(), "some");
	}

}
