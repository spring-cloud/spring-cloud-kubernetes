/*
 * Copyright 2012-2022 the original author or authors.
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

import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests only for the happy-path scenarios. All others are tested elsewhere.
 *
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class LabeledSecretContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final Map<String, String> PINK_LABEL = Map.of("color", "pink");

	private static final Map<String, String> BLUE_LABEL = Map.of("color", "blue");

	private static final ConfigUtils.Prefix UNSET = ConfigUtils.Prefix.UNSET;

	private static KubernetesClient mockClient;

	static {
		LABELS.put("label2", "value2");
		LABELS.put("label1", "value1");
	}

	@BeforeAll
	static void beforeAll() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, NAMESPACE);
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

	}

	@AfterEach
	void afterEach() {
		mockClient.secrets().inNamespace(NAMESPACE).delete();
	}

	/**
	 * we have a single secret deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("test-secret").withLabels(LABELS).endMetadata()
				.addToData("secretName", Base64.getEncoder().encodeToString("secretValue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, LABELS, true, UNSET);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("secret.test-secret.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("secretName", "secretValue"), sourceData.sourceData());

	}

	/**
	 * we have three secrets deployed. two of them have labels that match (color=red), one
	 * does not (color=blue).
	 */
	@Test
	void twoSecretsMatchAgainstLabels() {

		Secret redOne = new SecretBuilder().withNewMetadata().withName("red-secret").withLabels(RED_LABEL).endMetadata()
				.addToData("colorOne", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		Secret redTwo = new SecretBuilder().withNewMetadata().withName("red-secret-again").withLabels(RED_LABEL)
				.endMetadata().addToData("colorTwo", Base64.getEncoder().encodeToString("really-red-again".getBytes()))
				.build();

		Secret blue = new SecretBuilder().withNewMetadata().withName("blue-secret").withLabels(BLUE_LABEL).endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("blue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(redOne);
		mockClient.secrets().inNamespace(NAMESPACE).create(redTwo);
		mockClient.secrets().inNamespace(NAMESPACE).create(blue);

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, RED_LABEL, true, UNSET);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red-secret.red-secret-again.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("colorOne"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("colorTwo"), "really-red-again");

	}

	/**
	 * one secret deployed (pink), does not match our query (blue).
	 */
	@Test
	void secretNoMatch() {

		Secret pink = new SecretBuilder().withNewMetadata().withName("pink-secret").withLabels(PINK_LABEL).endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("pink".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(pink);

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, BLUE_LABEL, true, UNSET);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.color.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	/**
	 * LabeledSecretContextToSourceDataProvider gets as input a Fabric8ConfigContext. This
	 * context has a namespace as well as a NormalizedSource, that has a namespace too. It
	 * is easy to get confused in code on which namespace to use. This test makes sure
	 * that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("test-secret").withLabels(LABELS).endMetadata()
				.addToData("secretName", Base64.getEncoder().encodeToString("secretValue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		// different namespace
		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE + "nope", LABELS, true, UNSET);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("secret.test-secret.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("secretName", "secretValue"), sourceData.sourceData());
	}

	/**
	 * one secret with name : "blue-secret" and labels "color=blue" is deployed. we search
	 * it with the same labels, find it, and assert that name of the SourceData (it must
	 * use its name, not its labels) and values in the SourceData must be prefixed (since
	 * we have provided an explicit prefix).
	 */
	@Test
	void testWithPrefix() {
		Secret secret = new SecretBuilder().withNewMetadata().withName("blue-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("what-color", Base64.getEncoder().encodeToString("blue-color".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		ConfigUtils.Prefix mePrefix = ConfigUtils.findPrefix("me", false, false, "irrelevant");
		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, mePrefix);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
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
		Secret blueSecret = new SecretBuilder().withNewMetadata().withName("blue-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("first", Base64.getEncoder().encodeToString("blue".getBytes())).build();

		Secret anotherBlue = new SecretBuilder().withNewMetadata().withName("another-blue-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("second", Base64.getEncoder().encodeToString("blue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(blueSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(anotherBlue);

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
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
