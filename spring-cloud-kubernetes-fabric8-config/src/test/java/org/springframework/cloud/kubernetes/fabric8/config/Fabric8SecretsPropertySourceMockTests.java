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

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * tests that are supposed to use EnableKubernetesMockClient only
 *
 * @author wind57
 */
@EnableKubernetesMockClient
class Fabric8SecretsPropertySourceMockTests {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient client;

	@Test
	void namedStrategyShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		NamedSecretNormalizedSource named = new NamedSecretNormalizedSource(name, namespace, true, "");
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, named, "default", new MockEnvironment());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatThrownBy(() -> new Fabric8SecretsPropertySource(context)).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'");
	}

	@Test
	void labeledStrategyShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String namespace = "default";
		final Map<String, String> labels = Collections.singletonMap("a", "b");
		final String path = String.format("/api/v1/namespaces/%s/secrets?labelSelector=", namespace) + "a%3Db";

		LabeledSecretNormalizedSource labeled = new LabeledSecretNormalizedSource(namespace, labels, true, "");
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, labeled, "default", new MockEnvironment());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatThrownBy(() -> new Fabric8SecretsPropertySource(context)).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'");
	}

	@Test
	void namedStrategyShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		NamedSecretNormalizedSource named = new NamedSecretNormalizedSource(name, namespace, false, "");
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, named, "default", new MockEnvironment());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatNoException().isThrownBy(() -> new Fabric8SecretsPropertySource(context));
	}

	@Test
	void labeledStrategyShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String namespace = "default";
		final Map<String, String> labels = Collections.singletonMap("a", "b");
		final String path = String.format("/api/v1/namespaces/%s/secrets?labelSelector=", namespace) + "a%3Db";

		LabeledSecretNormalizedSource labeled = new LabeledSecretNormalizedSource(namespace, labels, false, "");
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, labeled, "default", new MockEnvironment());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatNoException().isThrownBy(() -> new Fabric8SecretsPropertySource(context));
	}

}
