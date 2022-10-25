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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
class Fabric8ConfigMapPropertySourceLocatorTests {

	private KubernetesMockServer mockServer;

	private KubernetesClient mockClient;

	private final DefaultKubernetesClient client = Mockito.mock(DefaultKubernetesClient.class);

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "irrelevant");

	@Test
	void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		String name = "my-config";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, name, namespace, false, true, true, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("api/v1/namespaces/default/configmaps. Message: Internal Server Error.");
	}

	@Test
	void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		String name = "my-config";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps/my-config";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, name, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatNoException().isThrownBy(() -> locator.locate(new MockEnvironment()));
	}

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
