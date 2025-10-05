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

package org.springframework.cloud.kubernetes.client.config;

import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesClientConfigMapErrorOnReadingSourceTests {

	private static final V1ConfigMapList SINGLE_CONFIGMAP_LIST = new V1ConfigMapList()
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(
					new V1ObjectMetaBuilder().withName("two").withNamespace("default").withResourceVersion("1").build())
			.build());

	private static final V1ConfigMapList DOUBLE_CONFIGMAP_LIST = new V1ConfigMapList()
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(
					new V1ObjectMetaBuilder().withName("one").withNamespace("default").withResourceVersion("1").build())
			.build())
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(
					new V1ObjectMetaBuilder().withName("two").withNamespace("default").withResourceVersion("1").build())
			.build());

	private static WireMockServer wireMockServer;

	@BeforeAll
	public static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterAll
	public static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	/**
	 * <pre>
	 *     we try to read all config maps in a namespace and fail.
	 * </pre>
	 */
	@Test
	void namedSingleConfigMapFails(CapturedOutput output) {
		String name = "my-config";
		String namespace = "spring-k8s";
		String path = "/api/v1/namespaces/" + namespace + "/configmaps";

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true, List.of(), Map.of(),
				true, name, namespace, false, true, false, RetryProperties.DEFAULT, ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failure in reading named sources");
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

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.willSetStateTo("go-to-next"));

		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(JSON.serialize(SINGLE_CONFIGMAP_LIST)))
			.inScenario("started")
			.whenScenarioStateIs("go-to-next")
			.willSetStateTo("done"));

		ConfigMapConfigProperties.Source sourceOne = new ConfigMapConfigProperties.Source(configMapNameOne, namespace,
				Map.of(), null, null, null);
		ConfigMapConfigProperties.Source sourceTwo = new ConfigMapConfigProperties.Source(configMapNameTwo, namespace,
				Map.of(), null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true,
				List.of(sourceOne, sourceTwo), Map.of(), true, null, namespace, false, true, false,
				RetryProperties.DEFAULT, ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// one property source is present
		assertThat(names).containsExactly("configmap.two.default");
		assertThat(output.getOut())
			.doesNotContain("sourceName : two was requested, but not found in namespace : default");
		assertThat(output.getOut()).contains("Failure in reading named sources");
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

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.willSetStateTo("go-to-next"));

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.whenScenarioStateIs("go-to-next")
			.willSetStateTo("done"));

		ConfigMapConfigProperties.Source sourceOne = new ConfigMapConfigProperties.Source(configMapNameOne, namespace,
				Map.of(), null, null, null);
		ConfigMapConfigProperties.Source sourceTwo = new ConfigMapConfigProperties.Source(configMapNameTwo, namespace,
				Map.of(), null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true,
				List.of(sourceOne, sourceTwo), Map.of(), true, null, namespace, false, true, false,
				RetryProperties.DEFAULT, ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut())
			.doesNotContain("sourceName : one was requested, but not found in namespace : default");
		assertThat(output.getOut())
			.doesNotContain("sourceName : two was requested, but not found in namespace : default");
		assertThat(output.getOut()).contains("Failure in reading named sources");
		assertThat(output.getOut()).contains("Failed to load source: { config-map name : 'Optional[one]'");
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
		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.willSetStateTo("go-to-next"));

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.whenScenarioStateIs("go-to-next")
			.willSetStateTo("done"));

		ConfigMapConfigProperties.Source configMapSource = new ConfigMapConfigProperties.Source(null, namespace, labels,
				null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true,
				List.of(configMapSource), labels, true, null, namespace, false, true, false, RetryProperties.DEFAULT,
				ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output.getOut()).contains("Failure in reading labeled sources");
		assertThat(output.getOut()).contains("Failure in reading named sources");
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

		Map<String, String> configMapOneLabels = Map.of("one", "1");
		Map<String, String> configMapTwoLabels = Map.of("two", "2");

		String namespace = "default";
		String path = "/api/v1/namespaces/default/configmaps";

		// one for 'application' named configmap and one for the first labeled configmap
		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.willSetStateTo("first"));

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.whenScenarioStateIs("first")
			.willSetStateTo("second"));

		// one that passes
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(JSON.serialize(DOUBLE_CONFIGMAP_LIST)))
			.inScenario("started")
			.whenScenarioStateIs("second")
			.willSetStateTo("done"));

		ConfigMapConfigProperties.Source sourceOne = new ConfigMapConfigProperties.Source(null, namespace,
				configMapOneLabels, null, null, null);
		ConfigMapConfigProperties.Source sourceTwo = new ConfigMapConfigProperties.Source(null, namespace,
				configMapTwoLabels, null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true,
				List.of(sourceOne, sourceTwo), Map.of("one", "1", "two", "2"), true, null, namespace, false, true,
				false, RetryProperties.DEFAULT, ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());
		List<String> names = propertySource.getPropertySources().stream().map(PropertySource::getName).toList();

		// one source is present
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
		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.willSetStateTo("first"));

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.whenScenarioStateIs("first")
			.willSetStateTo("second"));

		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("started")
			.whenScenarioStateIs("second")
			.willSetStateTo("done"));

		ConfigMapConfigProperties.Source sourceOne = new ConfigMapConfigProperties.Source(null, namespace,
				configMapOneLabels, null, null, null);
		ConfigMapConfigProperties.Source sourceTwo = new ConfigMapConfigProperties.Source(null, namespace,
				configMapTwoLabels, null, null, null);

		ConfigMapConfigProperties configMapConfigProperties = new ConfigMapConfigProperties(true,
				List.of(sourceOne, sourceTwo), Map.of("one", "1", "two", "2"), true, null, namespace, false, true,
				false, RetryProperties.DEFAULT, ReadType.BATCH);

		CoreV1Api api = new CoreV1Api();
		KubernetesClientConfigMapPropertySourceLocator locator = new KubernetesClientConfigMapPropertySourceLocator(api,
				configMapConfigProperties, new KubernetesNamespaceProvider(new MockEnvironment()));

		CompositePropertySource propertySource = (CompositePropertySource) locator.locate(new MockEnvironment());

		assertThat(propertySource.getPropertySources()).isEmpty();
		assertThat(output).contains("Failure in reading labeled sources");
		assertThat(output).contains("Failure in reading named sources");
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{one=1}'");
		assertThat(output.getOut()).contains("Failed to load source: { config map labels : '{two=2}'");
	}

}
