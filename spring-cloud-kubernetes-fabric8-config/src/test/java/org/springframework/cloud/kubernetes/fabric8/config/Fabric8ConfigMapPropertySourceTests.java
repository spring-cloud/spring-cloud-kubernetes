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
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
class Fabric8ConfigMapPropertySourceTests {

	private KubernetesMockServer mockServer;

	private KubernetesClient mockClient;

	private static final ConfigUtils.Prefix DEFAULT = ConfigUtils.findPrefix("default", false, false, "irrelevant");

	@AfterEach
	void afterEach() {
		new Fabric8ConfigMapsCache().discardAll();
	}

	@Test
	void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		String name = "my-config";
		String namespace = "default";
		String path = String.format("/api/v1/namespaces/%s/configmaps", namespace);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();
		NormalizedSource source = new NamedConfigMapNormalizedSource(name, namespace, true, DEFAULT, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "default", new MockEnvironment());
		assertThatThrownBy(() -> new Fabric8ConfigMapPropertySource(context)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("v1/namespaces/default/configmaps. Message: Internal Server Error.");
	}

	@Test
	void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		String name = "my-config";
		String namespace = "default";
		String path = String.format("/api/v1/namespaces/%s/configmaps/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();
		NormalizedSource source = new NamedConfigMapNormalizedSource(name, namespace, false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());
		assertThatNoException().isThrownBy(() -> new Fabric8ConfigMapPropertySource(context));
	}

}
