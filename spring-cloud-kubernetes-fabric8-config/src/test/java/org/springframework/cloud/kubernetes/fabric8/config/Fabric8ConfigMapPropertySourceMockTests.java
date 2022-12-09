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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8ConfigMapPropertySourceMockTests {

	private final KubernetesClient client = Mockito.mock(KubernetesClient.class);

	@Test
	void constructorWithClientNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn("namespace");
		NormalizedSource source = new NamedConfigMapNormalizedSource("configmap", null, false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, source, "", new MockEnvironment());
		assertThat(new Fabric8ConfigMapPropertySource(context)).isNotNull();
	}

	@Test
	void constructorWithNamespaceMustNotFail() {

		Mockito.when(client.getNamespace()).thenReturn(null);
		NormalizedSource source = new NamedConfigMapNormalizedSource("configMap", null, false, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, source, "", new MockEnvironment());
		assertThat(new Fabric8ConfigMapPropertySource(context)).isNotNull();
	}

}
