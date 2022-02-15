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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Collections;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPrefixContext;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
class NamedConfigMapContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static final String RED_CONFIG_MAP_NAME = "red";

	private static final String BLUE_CONFIG_MAP_NAME = "blue";

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
	}

	/**
	 * we have a single config map deployed. it does not match our query.
	 */
	@Test
	void noMatch() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME)
										.withNamespace(NAMESPACE).withResourceVersion("1").build())
								.addToData("color", "really-red").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(BLUE_CONFIG_MAP_NAME, NAMESPACE, true, "", false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());

	}

	/**
	 * we have a single config map deployed. it matches our query.
	 */
	@Test
	void match() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME)
										.withNamespace(NAMESPACE).withResourceVersion("1").build())
								.addToData("color", "really-red").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, "", false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "really-red"));

	}

	/**
	 * we have two config maps deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also.
	 */
	@Test
	void matchIncludeSingleProfile() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(
										new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE)
												.withResourceVersion("1").build())
								.addToData("color", "really-red").build())
				.addItemsItem(new V1ConfigMapBuilder()
						.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-profile")
								.withNamespace(NAMESPACE).withResourceVersion("1").build())
						.addToData("taste", "mango").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, "", true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-profile.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("taste"), "mango");

	}

	/**
	 * we have two config maps deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * config maps as well.
	 */
	@Test
	void matchIncludeSingleProfileWithPrefix() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(
										new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE)
												.withResourceVersion("1").build())
								.addToData("color", "really-red").build())
				.addItemsItem(new V1ConfigMapBuilder()
						.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-profile")
								.withNamespace(NAMESPACE).withResourceVersion("1").build())
						.addToData("taste", "mango").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, "some",
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-profile.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");

	}

	/**
	 * we have three config maps deployed. one matches the query name. the other two match
	 * the active profile + name, thus are taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * config maps as well.
	 */
	@Test
	void matchIncludeTwoProfilesWithPrefix() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(
										new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME).withNamespace(NAMESPACE)
												.withResourceVersion("1").build())
								.addToData("color", "really-red").build())
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(
										new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-taste")
												.withNamespace(NAMESPACE).withResourceVersion("1").build())
								.addToData("taste", "mango").build())
				.addItemsItem(new V1ConfigMapBuilder()
						.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME + "-with-shape")
								.withNamespace(NAMESPACE).withResourceVersion("1").build())
						.addToData("shape", "round").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE, true, "some",
				true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-taste", "with-shape");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-taste.red-with-shape.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 3);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");
		Assertions.assertEquals(sourceData.sourceData().get("some.shape"), "round");

	}

	// this test makes sure that even if NormalizedSource has no name (which is a valid
	// case for config maps),
	// it will default to "application" and such a config map will be read.
	@Test
	void matchWithoutName() {
		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("application")
						.withNamespace(NAMESPACE).withResourceVersion("1").build()).addToData("color", "red").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(null, NAMESPACE, true, "some", false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.application.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("some.color", "red"));
	}

	/**
	 * NamedSecretContextToSourceDataProvider gets as input a
	 * KubernetesClientConfigContext. This context has a namespace as well as a
	 * NormalizedSource, that has a namespace too. It is easy to get confused in code on
	 * which namespace to use. This test makes sure that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		V1ConfigMapList configMapList = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withName(RED_CONFIG_MAP_NAME)
										.withNamespace(NAMESPACE).withResourceVersion("1").build())
								.addToData("color", "really-red").build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
		NormalizedSource source = new NamedConfigMapNormalizedSource(RED_CONFIG_MAP_NAME, NAMESPACE + "nope", true, "",
				false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(Dummy::processEntries, Dummy::sourceName, Dummy::prefix).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "really-red"));
	}

	// needed only to allow access to the super methods
	private static final class Dummy extends ConfigMapPropertySource {

		private Dummy() {
			super(SourceData.emptyRecord("dummy-name"));
		}

		private static String sourceName(String name, String namespace) {
			return getSourceName(name, namespace);
		}

		private static Map<String, Object> processEntries(Map<String, String> map, Environment environment) {
			return processAllEntries(map, environment);
		}

		private static SourceData prefix(ConfigMapPrefixContext context) {
			return withPrefix(context);
		}

	}

}
