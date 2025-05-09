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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class NamedConfigMapContextToSourceDataProviderNonNamespacedBatchReadTests {

	private static final boolean NAMESPACED_BATCH_READ = false;

	private static final String NAMESPACE = "default";

	private static final String RED_CONFIG_MAP_NAME = "red";

	private static final String RED_WITH_PROFILE_CONFIG_MAP_NAME = RED_CONFIG_MAP_NAME + "-with-profile";

	private static final String BLUE_CONFIG_MAP_NAME = "blue";

	private static final Map<String, String> COLOR_REALLY_RED = Map.of("color", "really-red");

	private static final Map<String, String> TASTE_MANGO = Map.of("taste", "mango");

	@BeforeAll
	static void setup() {
		WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
		new KubernetesClientSourcesNamespaceBatched().discardConfigMaps();
	}

	@AfterAll
	static void afterAll() {
		WireMock.shutdownServer();
	}

	/**
	 * <pre>
	 *     one configmap deployed with name "red"
	 *     we search by name, but for the "blue" one, as such not find it
	 * </pre>
	 */
	@Test
	void noMatch() {
		V1ConfigMap redConfigMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(redConfigMap, "/api/v1/namespaces/default/configmaps/red");

		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(BLUE_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.blue.default");
		Assertions.assertThat(sourceData.sourceData()).isEmpty();

	}

	/**
	 * <pre>
	 *     one configmap deployed with name "red"
	 *     we search by name, for the "red" one, as such we find it
	 * </pre>
	 */
	@Test
	void match() {

		V1ConfigMap configMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(configMap, "/api/v1/namespaces/default/configmaps/red");

		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(sourceData.sourceData()).isEqualTo(COLOR_REALLY_RED);

	}

	/**
	 * <pre>
	 *     - two configmaps deployed : "red" and "red-with-profile".
	 * </pre>
	 */
	@Test
	void matchIncludeSingleProfile() {

		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(red, "/api/v1/namespaces/default/configmaps/red");

		V1ConfigMap redWithProfile = new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName(RED_WITH_PROFILE_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(TASTE_MANGO)
			.build();
		stubCall(redWithProfile, "/api/v1/namespaces/default/configmaps/red-with-profile");

		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true,
				ConfigUtils.Prefix.DEFAULT, true, true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-profile.default.with-profile");
		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(1);
		Assertions.assertThat(sourceData.sourceData().get("taste")).isEqualTo("mango");

	}

	/**
	 * <pre>
	 *     - two configmaps deployed : "red" and "red-with-profile".
	 *     - "red" is matched directly, "red-with-profile" is matched because we have an active profile
	 *       "active-profile"
	 *     -  This takes into consideration the prefix, that we explicitly specify.
	 *        Notice that prefix works for profile based config maps as well.
	 * </pre>
	 */
	@Test
	void matchIncludeSingleProfileWithPrefix() {

		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(red, "/api/v1/namespaces/default/configmaps/red");

		V1ConfigMap redWithTaste = new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName(RED_WITH_PROFILE_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(TASTE_MANGO)
			.build();
		stubCall(redWithTaste, "/api/v1/namespaces/default/configmaps/red-with-profile");

		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, prefix,
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-profile.default");
		Assertions.assertThat(sourceData.sourceData()).hasSize(2);
		Assertions.assertThat(sourceData.sourceData().get("some.color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("some.taste")).isEqualTo("mango");

	}

	/**
	 * <pre>
	 *     - three configmaps deployed : "red", "red-with-taste" and "red-with-shape"
	 *     - "red" is matched directly, the other two are matched because of active profiles
	 *     -  This takes into consideration the prefix, that we explicitly specify.
	 *        Notice that prefix works for profile based config maps as well.
	 * </pre>
	 */
	@Test
	void matchIncludeTwoProfilesWithPrefix() {

		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(red, "/api/v1/namespaces/default/configmaps/red");

		V1ConfigMap redWithTaste = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-taste")
				.withNamespace(NAMESPACE)
				.withResourceVersion("1")
				.build())
			.addToData(TASTE_MANGO)
			.build();
		stubCall(redWithTaste, "/api/v1/namespaces/default/configmaps/red-with-taste");

		V1ConfigMap redWithShape = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-shape")
				.withNamespace(NAMESPACE)
				.build())
			.addToData("shape", "round")
			.build();
		stubCall(redWithShape, "/api/v1/namespaces/default/configmaps/red-with-shape");

		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, prefix,
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-taste", "with-shape");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-shape.red-with-taste.default");
		Assertions.assertThat(sourceData.sourceData()).hasSize(3);
		Assertions.assertThat(sourceData.sourceData().get("some.color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("some.taste")).isEqualTo("mango");
		Assertions.assertThat(sourceData.sourceData().get("some.shape")).isEqualTo("round");

	}

	/**
	 * <pre>
	 * 		proves that an implicit configmap is not going to be generated and read
	 * </pre>
	 */
	@Test
	void matchWithName() {

		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("application").withNamespace(NAMESPACE).build())
			.addToData("color", "red")
			.build();
		stubCall(red, "/api/v1/namespaces/default/configmaps/red");

		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource("application", NAMESPACE, true, prefix, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.application.default");
		Assertions.assertThat(sourceData.sourceData()).isEmpty();
	}

	/**
	 * <pre>
	 *     - NamedSecretContextToSourceDataProvider gets as input a KubernetesClientConfigContext.
	 *     - This context has a namespace as well as a NormalizedSource, that has a namespace too.
	 *     - This test makes sure that we use the proper one.
	 * </pre>
	 */
	@Test
	void namespaceMatch() {

		V1ConfigMap configMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(COLOR_REALLY_RED)
			.build();
		stubCall(configMap, "/api/v1/namespaces/default/configmaps/red");

		CoreV1Api api = new CoreV1Api();

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, wrongNamespace, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(sourceData.sourceData()).isEqualTo(COLOR_REALLY_RED);
	}

	/**
	 * <pre>
	 *     - proves that single yaml file gets special treatment
	 * </pre>
	 */
	@Test
	void testSingleYaml() {
		V1ConfigMap singleYaml = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData("single.yaml", "key: value")
			.build();
		stubCall(singleYaml, "/api/v1/namespaces/default/configmaps/red");

		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(sourceData.sourceData()).containsExactlyInAnyOrderEntriesOf(Map.of("key", "value"));
	}

	/**
	 * <pre>
	 *     - one configmap is deployed with name "one"
	 *     - profile is enabled with name "k8s"
	 *
	 *     we assert that the name of the source is "one" and does not contain "one-dev"
	 * </pre>
	 */
	@Test
	void testCorrectNameWithProfile() {
		V1ConfigMap one = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("one").withNamespace(NAMESPACE).build())
			.addToData("key", "value")
			.build();
		stubCall(one, "/api/v1/namespaces/default/configmaps/one");

		CoreV1Api api = new CoreV1Api();

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource source = new NamedConfigMapNormalizedSource("one", NAMESPACE, true, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.one.default");
		Assertions.assertThat(sourceData.sourceData()).containsExactlyInAnyOrderEntriesOf(Map.of("key", "value"));
	}

	/**
	 * <pre>
	 *     - one configmap is deployed with name "red"
	 *     - one configmap is deployed with name "green"
	 *
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved again from the cluster, non cached.
	 * </pre>
	 */
	@Test
	void nonCache(CapturedOutput output) {
		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("red").withNamespace(NAMESPACE).build())
			.addToData("color", "red")
			.build();
		stubCall(red, "/api/v1/namespaces/default/configmaps/red");

		V1ConfigMap green = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("green").withNamespace(NAMESPACE).build())
			.addToData("color", "green")
			.build();
		stubCall(green, "/api/v1/namespaces/default/configmaps/green");

		CoreV1Api api = new CoreV1Api();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, false);
		KubernetesClientConfigContext redContext = new KubernetesClientConfigContext(api, redSource, NAMESPACE,
				environment, true, NAMESPACED_BATCH_READ);
		KubernetesClientContextToSourceData redData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertThat(redSourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(redSourceData.sourceData()).containsExactlyInAnyOrderEntriesOf(Map.of("color", "red"));

		Assertions.assertThat(output.getAll())
			.doesNotContain("Loaded all config maps in namespace '" + NAMESPACE + "'");
		Assertions.assertThat(output.getAll()).contains("Will read individual configmaps in namespace");

		NormalizedSource greenSource = new NamedConfigMapNormalizedSource("green", NAMESPACE, true, true);
		KubernetesClientConfigContext greenContext = new KubernetesClientConfigContext(api, greenSource, NAMESPACE,
				environment, false, NAMESPACED_BATCH_READ);
		KubernetesClientContextToSourceData greenData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertThat(greenSourceData.sourceName()).isEqualTo("configmap.green.default");
		Assertions.assertThat(greenSourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "green"));

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all config maps in namespace");
		Assertions.assertThat(out.length).isEqualTo(1);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Will read individual configmaps in namespace");
		Assertions.assertThat(out.length).isEqualTo(3);

	}

	private void stubCall(V1ConfigMap configMap, String path) {
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMap))));
	}

}
