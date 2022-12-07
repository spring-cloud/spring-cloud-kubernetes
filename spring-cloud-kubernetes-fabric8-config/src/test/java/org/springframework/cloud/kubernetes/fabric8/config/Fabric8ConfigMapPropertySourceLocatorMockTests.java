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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author wind57
 */
class Fabric8ConfigMapPropertySourceLocatorMockTests {

	private final KubernetesClient client = Mockito.mock(KubernetesClient.class);

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "irrelevant");

	@Test
	void constructorWithoutClientNamespaceMustFail() {

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, "name", null, false, true, false, RetryProperties.DEFAULT);

		Mockito.when(client.getNamespace()).thenReturn(null);
		Fabric8ConfigMapPropertySourceLocator source = new Fabric8ConfigMapPropertySourceLocator(client,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("name", null, false, PREFIX, false);
		assertThatThrownBy(() -> source.getMapPropertySource(normalizedSource, new MockEnvironment()))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

}
