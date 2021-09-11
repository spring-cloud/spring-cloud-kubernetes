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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;

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
	public void testNamespaceResolutionFailed() {
		assertThatThrownBy(() -> KubernetesClientConfigUtils.getApplicationNamespace("", "target", null))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

}
