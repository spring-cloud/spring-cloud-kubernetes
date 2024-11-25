/*
 * Copyright 2013-2024 the original author or authors.
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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties.Source;

/**
 * @author wind57
 */
@EnableKubernetesMockClient
@ExtendWith(OutputCaptureExtension.class)
class Fabric8SecretErrorOnReadingSourceTests {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void beforeAll() {
		mockClient.getConfiguration().setRequestRetryBackoffLimit(0);
	}

	/**
	 * <pre>
	 *     we try to read all secrets in a namespace and fail,
	 *     thus generate a well defined name for the source.
	 * </pre>
	 */
	@Test
	void namedSingleSecretFails(CapturedOutput output) {
		String name = "my-secret";
		String namespace = "spring-k8s";
		String path = "/api/v1/namespaces/" + namespace + "/secrets";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(),
				List.of(), true, name, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		MapPropertySource mapPropertySource = (MapPropertySource) propertySource.getPropertySources()
			.stream()
			.findAny()
			.orElseThrow();

		assertThat(mapPropertySource.getName()).isEqualTo("secret..spring-k8s");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");
		assertThat(output).contains("failure in reading named sources");

	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     one fails and one passes.
	 * </pre>
	 */
	@Test
	void namedTwoSecretsOneFails() {
		String secretNameOne = "one";
		String secretNameTwo = "two";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		Secret secretTwo = secret(secretNameTwo, Map.of());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		mockServer.expect().withPath(path).andReturn(200, new SecretListBuilder().withItems(secretTwo).build()).once();

		Source sourceOne = new Source(secretNameOne, namespace, Map.of(), null, null, null);
		Source sourceTwo = new Source(secretNameTwo, namespace, Map.of(), null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(),
				List.of(sourceOne, sourceTwo), true, null, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// two sources are present, one being empty
		assertThat(names).containsExactly("secret.two.default", "secret..default");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");

	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     both fail.
	 * </pre>
	 */
	@Test
	void namedTwoSecretsBothFail() {
		String secretNameOne = "one";
		String secretNameTwo = "two";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		Source sourceOne = new Source(secretNameOne, namespace, Map.of(), null, null, null);
		Source sourceTwo = new Source(secretNameTwo, namespace, Map.of(), null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(),
				List.of(sourceOne, sourceTwo), true, null, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		assertThat(names).containsExactly("secret..default");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");

	}

	/**
	 * <pre>
	 *     we try to read all secrets in a namespace and fail,
	 *     thus generate a well defined name for the source.
	 * </pre>
	 */
	@Test
	void labeledSingleSecretFails(CapturedOutput output) {
		Map<String, String> labels = Map.of("a", "b");
		String namespace = "spring-k8s";
		String path = "/api/v1/namespaces/" + namespace + "/secrets";

		// one for the 'application' named secret
		// the other for the labeled secret
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(2);

		Source secretSource = new Source(null, namespace, labels, null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, labels,
				List.of(secretSource), true, null, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> sourceNames = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		assertThat(sourceNames).containsExactly("secret..spring-k8s");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");
		assertThat(output).contains("failure in reading labeled sources");
		assertThat(output).contains("failure in reading named sources");
	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     one fails and one passes.
	 * </pre>
	 */
	@Test
	void labeledTwoSecretsOneFails(CapturedOutput output) {
		String secretNameOne = "one";
		String secretNameTwo = "two";

		Map<String, String> secretOneLabels = Map.of("one", "1");
		Map<String, String> secretTwoLabels = Map.of("two", "2");

		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		Secret secretOne = secret(secretNameOne, secretOneLabels);
		Secret secretTwo = secret(secretNameTwo, secretTwoLabels);

		// one for 'application' named secret and one for the first labeled secret
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(2);
		mockServer.expect()
			.withPath(path)
			.andReturn(200, new SecretListBuilder().withItems(secretOne, secretTwo).build())
			.once();

		Source sourceOne = new Source(null, namespace, secretOneLabels, null, null, null);
		Source sourceTwo = new Source(null, namespace, secretTwoLabels, null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true,
				Map.of("one", "1", "two", "2"), List.of(sourceOne, sourceTwo), true, null, namespace, false,
				true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// two sources are present, one being empty
		assertThat(names).containsExactly("secret.two.default", "secret..default");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");

		assertThat(output).contains("failure in reading labeled sources");
		assertThat(output).contains("failure in reading named sources");

	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     both fail.
	 * </pre>
	 */
	@Test
	void labeledTwoConfigMapsBothFail(CapturedOutput output) {

		Map<String, String> secretOneLabels = Map.of("one", "1");
		Map<String, String> secretTwoLabels = Map.of("two", "2");

		String namespace = "default";
		String path = "/api/v1/namespaces/default/secrets";

		// one for 'application' named configmap and two for the labeled configmaps
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(3);

		Source sourceOne = new Source(null, namespace, secretOneLabels, null, null, null);
		Source sourceTwo = new Source(null, namespace, secretTwoLabels, null, null, null);

		SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true,
				Map.of("one", "1", "two", "2"), List.of(sourceOne, sourceTwo), true, null, namespace, false,
				true, false, RetryProperties.DEFAULT);

		Fabric8SecretsPropertySourceLocator locator = new Fabric8SecretsPropertySourceLocator(mockClient,
				secretsConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		assertThat(names).containsExactly("secret..default");
		assertThat(propertySource.getProperty(Constants.ERROR_PROPERTY)).isEqualTo("true");

		assertThat(output).contains("failure in reading labeled sources");
		assertThat(output).contains("failure in reading named sources");

	}

	private Secret secret(String name, Map<String, String> labels) {
		return new SecretBuilder().withNewMetadata().withName(name).withLabels(labels).endMetadata().build();
	}

}
