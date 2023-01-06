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
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@ExtendWith(OutputCaptureExtension.class)
class NamedSecretContextToSourceDataProviderTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("some", false, false, "irrelevant");

	private static final String NAMESPACE = "default";

	private static final Map<String, byte[]> COLOR_REALLY_RED = Map.of("color", "really-red".getBytes());

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
		new KubernetesClientSecretsCache().discardAll();
	}

	/**
	 * we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();
		V1SecretList secretList = new V1SecretList().addItemsItem(red);
		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}

	/**
	 * we have three secrets deployed. one of them has a name that matches (red), the
	 * other two have different names, thus no match.
	 */
	@Test
	void twoSecretMatchAgainstLabels() {

		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1Secret blue = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("blue").build())
				.addToData(COLOR_REALLY_RED).build();

		V1Secret pink = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("pink").build())
				.addToData(COLOR_REALLY_RED).build();

		V1SecretList secretList = new V1SecretListBuilder().addToItems(red).addToItems(blue).addToItems(pink).build();

		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		// blue does not match red, nor pink
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 1);
		Assertions.assertEquals(sourceData.sourceData().get("color"), "really-red");

	}

	/**
	 * one secret deployed (pink), does not match our query (blue).
	 */
	@Test
	void testSecretNoMatch() {

		V1Secret secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1SecretList secretList = new V1SecretList().addItemsItem(secret);
		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("blue", NAMESPACE, false, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	/**
	 * <pre>
	 *     - LabeledSecretContextToSourceDataProvider gets as input a KubernetesClientConfigContext.
	 *     - This context has a namespace as well as a NormalizedSource, that has a namespace too.
	 *     - This test makes sure that we use the proper one.
	 * </pre>
	 */
	@Test
	void namespaceMatch() {

		V1Secret secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1SecretList secretList = new V1SecretList().addItemsItem(secret);
		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource source = new NamedSecretNormalizedSource("red", wrongNamespace, false, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));
	}

	/**
	 * we have two secrets deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also.
	 */
	@Test
	void matchIncludeSingleProfile() {

		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1Secret mango = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red-with-profile").build())
				.addToData("taste", "mango".getBytes()).build();

		V1SecretList secretList = new V1SecretList().addItemsItem(red).addItemsItem(mango);

		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false, true);
		MockEnvironment environment = new MockEnvironment();
		environment.addActiveProfile("with-profile");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.red-with-profile.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("taste"), "mango");

	}

	/**
	 * we have two secrets deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * secrets as well.
	 */
	@Test
	void matchIncludeSingleProfileWithPrefix() {

		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1Secret mango = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red-with-taste").build())
				.addToData("taste", "mango".getBytes()).build();

		V1SecretList secretList = new V1SecretList().addItemsItem(red).addItemsItem(mango);

		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);
		MockEnvironment environment = new MockEnvironment();
		environment.addActiveProfile("with-taste");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.red-with-taste.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");

	}

	/**
	 * we have three secrets deployed. one matches the query name. the other two match the
	 * active profile + name, thus are taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * config maps as well.
	 */
	@Test
	void matchIncludeTwoProfilesWithPrefix() {

		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red").build())
				.addToData(COLOR_REALLY_RED).build();

		V1Secret mango = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red-with-taste").build())
				.addToData("taste", "mango".getBytes()).build();

		V1Secret shape = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red-with-shape").build())
				.addToData("shape", "round".getBytes()).build();

		V1SecretList secretList = new V1SecretList().addItemsItem(red).addItemsItem(mango).addItemsItem(shape);

		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("with-taste", "with-shape");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.red-with-shape.red-with-taste.default");

		Assertions.assertEquals(sourceData.sourceData().size(), 3);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");
		Assertions.assertEquals(sourceData.sourceData().get("some.shape"), "round");

	}

	/**
	 * <pre>
	 *     - proves that single yaml file gets special treatment
	 * </pre>
	 */
	@Test
	void testSingleYaml() {
		V1Secret singleYaml = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("single-yaml").withNamespace(NAMESPACE).build())
				.addToData("single.yaml", "key: value".getBytes()).build();
		V1SecretList secretList = new V1SecretList().addItemsItem(singleYaml);

		stubCall(secretList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new NamedSecretNormalizedSource("single-yaml", NAMESPACE, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.single-yaml.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("key", "value"));
	}

	/**
	 * <pre>
	 *     - one secret is deployed with name "red"
	 *     - one secret is deployed with name "green"
	 *
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {
		V1Secret red = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("red").withNamespace(NAMESPACE).build())
				.addToData("color", "red".getBytes()).build();

		V1Secret green = new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("green").withNamespace(NAMESPACE).build())
				.addToData("color", "green".getBytes()).build();

		V1SecretList configMapList = new V1SecretList().addItemsItem(red).addItemsItem(green);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, false);
		KubernetesClientConfigContext redContext = new KubernetesClientConfigContext(api, redSource, NAMESPACE,
				environment);
		KubernetesClientContextToSourceData redData = new NamedSecretContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(redSourceData.sourceData(), Map.of("color", "red"));
		Assertions.assertTrue(output.getAll().contains("Loaded all secrets in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenSource = new NamedSecretNormalizedSource("green", NAMESPACE, true, true);
		KubernetesClientConfigContext greenContext = new KubernetesClientConfigContext(api, greenSource, NAMESPACE,
				environment);
		KubernetesClientContextToSourceData greenData = new NamedSecretContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceName(), "secret.green.default");
		Assertions.assertEquals(greenSourceData.sourceData(), Map.of("color", "green"));

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

	}

	private void stubCall(V1SecretList list) {
		stubFor(get("/api/v1/namespaces/default/secrets")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(list))));
	}

}
