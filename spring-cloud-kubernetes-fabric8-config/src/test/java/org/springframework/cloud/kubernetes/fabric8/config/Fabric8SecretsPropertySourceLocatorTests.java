/*
 * Copyright 2013-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Isik Erhan
 */
@EnableKubernetesMockClient
@ExtendWith(OutputCaptureExtension.class)
class Fabric8SecretsPropertySourceLocatorTests {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void beforeAll() {
		mockClient.getConfiguration().setRequestRetryBackoffInterval(1);
	}

	@Test
	void locateShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		String name = "my-secret";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, name, namespace, false, true, true, RetryProperties.DEFAULT, ReadType.BATCH);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		assertThatThrownBy(() -> locator.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("v1/namespaces/default/secrets. Message: Internal Server Error.");
	}

	@Test
	void locateShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled(CapturedOutput output) {
		String name = "my-secret";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets/my-secret";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").always();

		SecretsConfigProperties configMapConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
				List.of(), true, name, namespace, false, true, false, RetryProperties.DEFAULT, ReadType.BATCH);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		List<PropertySource<?>> result = new ArrayList<>();
		assertThatNoException().isThrownBy(() -> {
			PropertySource<?> source = locator.locate(new MockEnvironment());
			result.add(source);
		});

		assertThat(result.get(0)).isInstanceOf(CompositePropertySource.class);
		CompositePropertySource composite = (CompositePropertySource) result.get(0);
		assertThat(composite.getPropertySources()).hasSize(0);
		assertThat(output.getOut()).contains("Failed to load source:");

	}

}
