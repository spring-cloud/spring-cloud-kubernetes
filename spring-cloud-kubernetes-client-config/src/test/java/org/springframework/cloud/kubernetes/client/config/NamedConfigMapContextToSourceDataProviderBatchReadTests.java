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

import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
class NamedConfigMapContextToSourceDataProviderBatchReadTests {

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
		KubernetesClientSourcesBatchRead.discardConfigMaps();
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

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(redConfigMap);
		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(BLUE_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, ReadType.BATCH);

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

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMap);
		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, ReadType.BATCH);

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

		V1ConfigMap redWithProfile = new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName(RED_WITH_PROFILE_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(TASTE_MANGO)
			.build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red).addItemsItem(redWithProfile);
		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true,
				ConfigUtils.Prefix.DEFAULT, true);
		MockEnvironment environment = new MockEnvironment();
		environment.addActiveProfile("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-profile.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "really-red", "taste", "mango"));

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

		V1ConfigMap redWithTaste = new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName(RED_WITH_PROFILE_CONFIG_MAP_NAME).withNamespace(NAMESPACE).build())
			.addToData(TASTE_MANGO)
			.build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red).addItemsItem(redWithTaste);
		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, prefix,
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-profile.default");
		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(2);
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

		V1ConfigMap redWithTaste = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-taste")
				.withNamespace(NAMESPACE)
				.withResourceVersion("1")
				.build())
			.addToData(TASTE_MANGO)
			.build();

		V1ConfigMap redWithShape = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-shape")
				.withNamespace(NAMESPACE)
				.build())
			.addToData("shape", "round")
			.build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red)
			.addItemsItem(redWithTaste)
			.addItemsItem(redWithShape);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, prefix,
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-taste", "with-shape");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.red-with-shape.red-with-taste.default");
		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(3);
		Assertions.assertThat(sourceData.sourceData().get("some.color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("some.taste")).isEqualTo("mango");
		Assertions.assertThat(sourceData.sourceData().get("some.shape")).isEqualTo("round");

	}

	/**
	 * <pre>
	 * 		proves that an implicit configmap is going to be generated and read, even if
	 * 	    we did not provide one
	 * </pre>
	 */
	@Test
	void matchWithName() {

		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("application").withNamespace(NAMESPACE).build())
			.addToData("color", "red")
			.build();
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("some", false, false, null);
		NormalizedSource source = new NamedConfigMapNormalizedSource("application", NAMESPACE, true, prefix, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.application.default");
		Assertions.assertThat(sourceData.sourceData()).isEqualTo(Collections.singletonMap("some.color", "red"));
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
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(configMap);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, wrongNamespace, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, ReadType.BATCH);

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
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(singleYaml);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment(), true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(sourceData.sourceData()).isEqualTo(Map.of("key", "value"));
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
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource source = new NamedConfigMapNormalizedSource("one", NAMESPACE, true, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
				true, ReadType.BATCH);

		KubernetesClientContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.one.default");
		Assertions.assertThat(sourceData.sourceData()).isEqualTo(Map.of("key", "value"));
	}

	/**
	 * <pre>
	 *     - one configmap is deployed with name "red"
	 *     - one configmap is deployed with name "green"
	 *
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache() {
		V1ConfigMap red = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("red").withNamespace(NAMESPACE).build())
			.addToData("color", "red")
			.build();

		V1ConfigMap green = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("green").withNamespace(NAMESPACE).build())
			.addToData("color", "green")
			.build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red).addItemsItem(green);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, false);
		KubernetesClientConfigContext redContext = new KubernetesClientConfigContext(api, redSource, NAMESPACE,
				environment, true, ReadType.BATCH);
		KubernetesClientContextToSourceData redData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertThat(redSourceData.sourceName()).isEqualTo("configmap.red.default");
		Assertions.assertThat(redSourceData.sourceData()).isEqualTo(Map.of("color", "red"));

		NormalizedSource greenSource = new NamedConfigMapNormalizedSource("green", NAMESPACE, true, true);
		KubernetesClientConfigContext greenContext = new KubernetesClientConfigContext(api, greenSource, NAMESPACE,
				environment, false, ReadType.BATCH);
		KubernetesClientContextToSourceData greenData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertThat(greenSourceData.sourceName()).isEqualTo("configmap.green.default");
		Assertions.assertThat(greenSourceData.sourceData()).isEqualTo(Map.of("color", "green"));

		// called only once, since the use caching
		verify(1, getRequestedFor(urlEqualTo("/api/v1/namespaces/default/configmaps")));
	}

	private void stubCall(V1ConfigMapList list) {
		stubFor(get("/api/v1/namespaces/default/configmaps")
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(list))));
	}

}
