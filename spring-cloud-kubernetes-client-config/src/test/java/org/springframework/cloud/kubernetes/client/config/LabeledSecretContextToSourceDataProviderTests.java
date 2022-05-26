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

import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
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
class LabeledSecretContextToSourceDataProviderTests {

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final String NAMESPACE = "default";

	static {
		LABELS.put("label2", "value2");
		LABELS.put("label1", "value1");
	}

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
	 * we have a single secret deployed. it does not match our query.
	 */
	@Test
	void noMatch() {

		V1SecretList secretList = new V1SecretList().addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(Collections.singletonMap("color", "red"))
						.withNamespace(NAMESPACE).withName("red-secret").withResourceVersion("1").build())
				.addToData("color", Base64.getEncoder().encode("really-red".getBytes())).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dred")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.color.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());

	}

	/**
	 * we have a single secret deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1SecretList SECRETS_LIST = new V1SecretList().addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(LABELS).withNamespace(NAMESPACE)
						.withResourceVersion("1").withName("test-secret").build())
				.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%2Clabel1%3Dvalue1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, LABELS, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.test-secret.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}

	/**
	 * we have two secrets deployed. both of them have labels that match (color=red).
	 */
	@Test
	void twoSecretsMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList();
		secretList.addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(RED_LABEL).withNamespace(NAMESPACE)
						.withResourceVersion("1").withName("color-one").build())
				.addToData("colorOne", "really-red-one".getBytes()).build());

		secretList.addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(RED_LABEL).withNamespace(NAMESPACE)
						.withResourceVersion("1").withName("color-two").build())
				.addToData("colorTwo", "really-red-two".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dred")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, RED_LABEL, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.color-one.color-two.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("colorOne"), "really-red-one");
		Assertions.assertEquals(sourceData.sourceData().get("colorTwo"), "really-red-two");

	}

	@Test
	void namespaceMatch() {
		V1SecretList SECRETS_LIST = new V1SecretList().addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(LABELS).withNamespace(NAMESPACE)
						.withResourceVersion("1").withName("test-secret").build())
				.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%2Clabel1%3Dvalue1")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE + "nope", LABELS, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.test-secret.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));
	}

	/**
	 * one secret with name : "blue-secret" and labels "color=blue" is deployed. we search
	 * it with the same labels, find it, and assert that name of the SourceData (it must
	 * use its name, not its labels) and values in the SourceData must be prefixed (since
	 * we have provided an explicit prefix).
	 */
	@Test
	void testWithPrefix() {

		V1SecretList SECRETS_LIST = new V1SecretList().addItemsItem(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue")).withNamespace(NAMESPACE)
						.withResourceVersion("1").withName("blue-secret").build())
				.addToData("what-color", "blue-color".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("me", false, false, null);
		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false, prefix);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("secret.blue-secret.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("me.what-color", "blue-color"), sourceData.sourceData());
	}

	/**
	 * two secrets are deployed (name:blue-secret, name:another-blue-secret) and labels
	 * "color=blue" (on both). we search with the same labels, find them, and assert that
	 * name of the SourceData (it must use its name, not its labels) and values in the
	 * SourceData must be prefixed (since we have provided a delayed prefix).
	 *
	 * Also notice that the prefix is made up from both secret names.
	 *
	 */
	@Test
	void testTwoSecretsWithPrefix() {
		V1SecretList SECRETS_LIST = new V1SecretList()
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(
										new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
												.withNamespace(NAMESPACE).withResourceVersion("1").withName(
														"blue-secret")
												.build())
								.addToData("first", "blue".getBytes()).build())
				.addItemsItem(new V1SecretBuilder()
						.withMetadata(
								new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue")).withNamespace(NAMESPACE)
										.withResourceVersion("1").withName("another-blue-secret").build())
						.addToData("second", "blue".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
				ConfigUtils.Prefix.DELAYED);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		// maps don't have a defined order, so assert components separately
		Assertions.assertEquals(46, sourceData.sourceName().length());
		Assertions.assertTrue(sourceData.sourceName().contains("secret"));
		Assertions.assertTrue(sourceData.sourceName().contains("blue-secret"));
		Assertions.assertTrue(sourceData.sourceName().contains("another-blue-secret"));
		Assertions.assertTrue(sourceData.sourceName().contains("default"));

		Map<String, Object> properties = sourceData.sourceData();
		Assertions.assertEquals(2, properties.size());
		Iterator<String> keys = properties.keySet().iterator();
		String firstKey = keys.next();
		String secondKey = keys.next();

		if (firstKey.contains("first")) {
			Assertions.assertEquals(firstKey, "another-blue-secret.blue-secret.first");
		}

		Assertions.assertEquals(secondKey, "another-blue-secret.blue-secret.second");
		Assertions.assertEquals(properties.get(firstKey), "blue");
		Assertions.assertEquals(properties.get(secondKey), "blue");
	}

}
