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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties.Source;

/**
 * @author wind57
 */
@EnableKubernetesMockClient
@ExtendWith(OutputCaptureExtension.class)
class Fabric8ConfigMapErrorOnReadingSourceTests {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void beforeAll() {
		mockClient.getConfiguration().setRequestRetryBackoffLimit(0);
	}

	/**
	 * <pre>
	 *     we try to read all config maps in a namespace and fail,
	 *     thus the composite property source is empty.
	 * </pre>
	 */
	@Test
	void namedSingleConfigMapFails(CapturedOutput output) {
		String name = "my-config";
		String namespace = "spring-k8s";
		String path = "/api/v1/namespaces/" + namespace + "/configmaps";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), List.of(),
				Map.of(), true, name, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failed to load source: { config-map name : 'Optional[my-config]'");
	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     one fails and one passes.
	 * </pre>
	 */
	@Test
	void namedTwoConfigMapsOneFails(CapturedOutput output) {
		String configMapNameOne = "one";
		String configMapNameTwo = "two";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		ConfigMap configMapTwo = configMap(configMapNameTwo, Map.of());

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		mockServer.expect()
			.withPath(path)
			.andReturn(200, new ConfigMapListBuilder().withItems(configMapTwo).build())
			.once();

		Source sourceOne = new Source(configMapNameOne, namespace, Map.of(), null, null, null);
		Source sourceTwo = new Source(configMapNameTwo, namespace, Map.of(), null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(sourceOne, sourceTwo), Map.of(), true, null, namespace, false, true, false,
				RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// one source is present
		assertThat(names).containsExactly("configmap.two.default");
		assertThat(output.getOut()).contains("Failed to load source: { config-map name : 'Optional[one]'");
	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     both fail.
	 * </pre>
	 */
	@Test
	void namedTwoConfigMapsBothFail(CapturedOutput output) {
		String configMapNameOne = "one";
		String configMapNameTwo = "two";
		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();

		Source sourceOne = new Source(configMapNameOne, namespace, Map.of(), null, null, null);
		Source sourceTwo = new Source(configMapNameTwo, namespace, Map.of(), null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(sourceOne, sourceTwo), Map.of(), true, null, namespace, false, true, false,
				RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failed to load source: { config-map name : 'Optional[one]'");
		assertThat(output.getOut()).contains("Failed to load source: { config-map name : 'Optional[two]'");
	}

	/**
	 * <pre>
	 *     we try to read all config maps in a namespace and fail.
	 * </pre>
	 */
	@Test
	void labeledSingleConfigMapFails(CapturedOutput output) {
		Map<String, String> labels = Map.of("a", "b");
		String namespace = "spring-k8s";
		String path = "/api/v1/namespaces/" + namespace + "/configmaps";

		// one for the 'application' named configmap
		// the other for the labeled config map
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(2);

		Source configMapSource = new Source(null, namespace, labels, null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(configMapSource), labels, true, null, namespace, false, true, false, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> sourceNames = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{a=b}'");
	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     one fails and one passes.
	 * </pre>
	 */
	@Test
	void labeledTwoConfigMapsOneFails(CapturedOutput output) {
		String configMapNameOne = "one";
		String configMapNameTwo = "two";

		Map<String, String> configMapOneLabels = Map.of("one", "1");
		Map<String, String> configMapTwoLabels = Map.of("two", "2");

		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		ConfigMap configMapOne = configMap(configMapNameOne, configMapOneLabels);
		ConfigMap configMapTwo = configMap(configMapNameTwo, configMapTwoLabels);

		// one for 'application' named configmap and one for the first labeled configmap
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(2);
		mockServer.expect()
			.withPath(path)
			.andReturn(200, new ConfigMapListBuilder().withItems(configMapOne, configMapTwo).build())
			.once();

		Source sourceOne = new Source(null, namespace, configMapOneLabels, null, null, null);
		Source sourceTwo = new Source(null, namespace, configMapTwoLabels, null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(sourceOne, sourceTwo), Map.of("one", "1", "two", "2"), true, null, namespace, false, true,
				false, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// one property source is present
		assertThat(names).containsExactly("configmap.two.default");
		assertThat(output.getOut()).contains("Failure in reading labeled sources");
		assertThat(output.getOut()).contains("Failure in reading named sources");
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{one=1}'");

	}

	/**
	 * <pre>
	 *     there are two sources and we try to read them.
	 *     both fail.
	 * </pre>
	 */
	@Test
	void labeledTwoConfigMapsBothFail(CapturedOutput output) {

		Map<String, String> configMapOneLabels = Map.of("one", "1");
		Map<String, String> configMapTwoLabels = Map.of("two", "2");

		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		// one for 'application' named configmap and two for the labeled configmaps
		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").times(3);

		Source sourceOne = new Source(null, namespace, configMapOneLabels, null, null, null);
		Source sourceTwo = new Source(null, namespace, configMapTwoLabels, null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(),
				List.of(sourceOne, sourceTwo), Map.of("one", "1", "two", "2"), true, null, namespace, false, true,
				false, RetryProperties.DEFAULT);

		Fabric8ConfigMapPropertySourceLocator locator = new Fabric8ConfigMapPropertySourceLocator(mockClient,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failure in reading labeled sources");
		assertThat(output.getOut()).contains("Failure in reading named sources");
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{two=2}'");
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{one=1}'");
	}

	private ConfigMap configMap(String name, Map<String, String> labels) {
		return new ConfigMapBuilder().withNewMetadata().withName(name).withLabels(labels).endMetadata().build();
	}

}
