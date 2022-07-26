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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.springframework.cloud.kubernetes.commons.config.StrictProfile;
import org.springframework.cloud.kubernetes.commons.config.StrictSourceNotFoundException;
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

	private static final LinkedHashSet<StrictProfile> EMPTY = new LinkedHashSet<>();

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

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, LABELS, true, EMPTY, false);
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

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, RED_LABEL, true, EMPTY, false);
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

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, BLUE_LABEL, true, EMPTY,
				false);
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
		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE + "nope", LABELS, true, EMPTY,
				false);
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
				Collections.singletonMap("color", "blue"), true, mePrefix, EMPTY, false);
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
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.another-blue-secret.blue-secret.default");

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

	/**
	 * two secrets are deployed: secret "color-secret" with label: "{color:blue}" and
	 * "color-secret-k8s" with no labels. We search by "{color:red}", do not find anything
	 * and thus have an empty SourceData. profile based sources are enabled, but it has no
	 * effect.
	 */
	@Test
	void searchWithLabelsNoSecretFound() {
		Secret colorSecret = new SecretBuilder().withNewMetadata().withName("color-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("one", Base64.getEncoder().encodeToString("1".getBytes())).build();

		Secret colorSecretK8s = new SecretBuilder().withNewMetadata().withName("color-secret-k8s").endMetadata()
				.addToData("two", Base64.getEncoder().encodeToString("2".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecretK8s);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DEFAULT, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertTrue(sourceData.sourceData().isEmpty());
		Assertions.assertEquals(sourceData.sourceName(), "secret.color.default");

	}

	/**
	 * two secrets are deployed: secret "color-secret" with label: "{color:blue}" and
	 * "shape-secret" with label: "{shape:round}". We search by "{color:blue}" and find
	 * one secret. profile based sources are enabled, but it has no effect.
	 */
	@Test
	void searchWithLabelsOneSecretFound() {
		Secret colorSecret = new SecretBuilder().withNewMetadata().withName("color-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("one", Base64.getEncoder().encodeToString("1".getBytes())).build();

		Secret shapeSecret = new SecretBuilder().withNewMetadata().withName("shape-secret").endMetadata()
				.addToData("two", Base64.getEncoder().encodeToString("2".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(shapeSecret);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, EMPTY, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 1);
		Assertions.assertEquals(sourceData.sourceData().get("one"), "1");
		Assertions.assertEquals(sourceData.sourceName(), "secret.color-secret.default");

	}

	/**
	 * two secrets are deployed: secret "color-secret" with label: "{color:blue}" and
	 * "color-secret-k8s" with label: "{color:red}". We search by "{color:blue}" and find
	 * one secret. Since profiles are enabled, we will also be reading "color-secret-k8s",
	 * even if its labels do not match provided ones.
	 */
	@Test
	void searchWithLabelsOneSecretFoundAndOneFromProfileFound() {
		Secret colorSecret = new SecretBuilder().withNewMetadata().withName("color-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("one", Base64.getEncoder().encodeToString("1".getBytes())).build();

		Secret colorSecretK8s = new SecretBuilder().withNewMetadata().withName("color-secret-k8s")
				.withLabels(Collections.singletonMap("color", "red")).endMetadata()
				.addToData("two", Base64.getEncoder().encodeToString("2".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecretK8s);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("color-secret.color-secret-k8s.one"), "1");
		Assertions.assertEquals(sourceData.sourceData().get("color-secret.color-secret-k8s.two"), "2");
		Assertions.assertEquals(sourceData.sourceName(), "secret.color-secret.color-secret-k8s.default");

	}

	/**
	 * <pre>
	 *     - secret "color-secret" with label "{color:blue}"
	 *     - secret "shape-secret" with labels "{color:blue, shape:round}"
	 *     - secret "no-fit" with labels "{tag:no-fit}"
	 *     - secret "color-secret-k8s" with label "{color:red}"
	 *     - secret "shape-secret-k8s" with label "{shape:triangle}"
	 * </pre>
	 */
	@Test
	void searchWithLabelsTwoSecretsFoundAndOneFromProfileFound() {
		Secret colorSecret = new SecretBuilder().withNewMetadata().withName("color-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("one", Base64.getEncoder().encodeToString("1".getBytes())).build();

		Secret shapeSecret = new SecretBuilder().withNewMetadata().withName("shape-secret")
				.withLabels(Map.of("color", "blue", "shape", "round")).endMetadata()
				.addToData("two", Base64.getEncoder().encodeToString("2".getBytes())).build();

		Secret noFit = new SecretBuilder().withNewMetadata().withName("no-fit").withLabels(Map.of("tag", "no-fit"))
				.endMetadata().addToData("three", Base64.getEncoder().encodeToString("3".getBytes())).build();

		Secret colorSecretK8s = new SecretBuilder().withNewMetadata().withName("color-secret-k8s")
				.withLabels(Map.of("color", "red")).endMetadata()
				.addToData("four", Base64.getEncoder().encodeToString("4".getBytes())).build();

		Secret shapeSecretK8s = new SecretBuilder().withNewMetadata().withName("shape-secret-k8s")
				.withLabels(Map.of("shape", "triangle")).endMetadata()
				.addToData("five", Base64.getEncoder().encodeToString("5".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(shapeSecret);
		mockClient.secrets().inNamespace(NAMESPACE).create(noFit);
		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecretK8s);
		mockClient.secrets().inNamespace(NAMESPACE).create(shapeSecretK8s);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 4);
		Assertions.assertEquals(
				sourceData.sourceData().get("color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.one"), "1");
		Assertions.assertEquals(
				sourceData.sourceData().get("color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.two"), "2");
		Assertions.assertEquals(
				sourceData.sourceData().get("color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.four"), "4");
		Assertions.assertEquals(
				sourceData.sourceData().get("color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.five"), "5");

		Assertions.assertEquals(sourceData.sourceName(),
				"secret.color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.default");

	}

	/**
	 * yaml/properties gets special treatment
	 */
	@Test
	void testYaml() {
		Secret colorSecret = new SecretBuilder().withNewMetadata().withName("color-secret")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("test.yaml", Base64.getEncoder().encodeToString("color: blue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(colorSecret);

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, EMPTY, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 1);
		Assertions.assertEquals(sourceData.sourceData().get("color"), "blue");
		Assertions.assertEquals(sourceData.sourceName(), "secret.color-secret.default");
	}

	/**
	 * <pre>
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secrets:
	 *             sources:
	 *               - labels:
	 *               	 color: red
	 *                 strict: false
	 *
	 *     - we want to read a secret with labels "color: red".
	 *     - there are no secrets in the namespace at all and since "strict: false",
	 *       we will not fail.
	 *
	 * </pre>
	 */
	@Test
	void testStrictOne() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, new LinkedHashSet<>(),
			false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.color.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 0);
	}

	/**
	 * <pre>
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secrets:
	 *             sources:
	 *               - labels:
	 *                   color: red
	 *                 strict: true
	 *
	 *     - we want to read a secret with labels "color: red".
	 *     - since there are no secrets in the namespace and "strict: true", we will fail
	 * </pre>
	 */
	@Test
	void testStrictTwo() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, new LinkedHashSet<>(),
			true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
			() -> data.apply(context));

		Assertions.assertEquals(ex.getMessage(), "secret(s) with labels : {color=red} not found in namespace: default");
	}

	/**
	 * <pre>
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secrets:
	 *             sources:
	 *               - labels:
	 *                   color: red
	 *                 strict: false
	 *                 strict-for-profiles:
	 *                   - dev
	 *
	 *     - we want to read a secret with labels "color: red" and it has "strict=false", it is not present
	 *       in the namespace, but since "strict=false", we will not fail.
	 *     - "strict-for-profiles = dev", but there are no secrets at all in the namespace, so we fail
	 * </pre>
	 */
	@Test
	void testStrictThree() {
		MockEnvironment environment = new MockEnvironment();

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("dev", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true,
			profiles, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
			() -> data.apply(context));

		Assertions.assertEquals(
			ex.getMessage(),
			"profile based secret with profile: dev and labels : {color=red} not found in namespace: default");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secrets:
	 *             sources:
	 *               - labels:
	 *                   color: red
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a secret with labels : {color: red} (name is 'a') and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a secret "a-us-west" is not present, and since
	 *       it is not part of the "strict-for-profiles", it will be skipped
	 *
	 * </pre>
	 */
	@Test
	void testStrictFour() {

		Secret a = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8))).build();

		Secret aK8s = new SecretBuilder().withNewMetadata().withName("a-k8s").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-k8s".getBytes(StandardCharsets.UTF_8))).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(aK8s);
		mockClient.secrets().inNamespace(NAMESPACE).create(a);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		environment.setActiveProfiles("us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a.a-k8s.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-k8s");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secret:
	 *             sources:
	 *               - labels:
	 *                   color: red
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                   - us-west
	 *
	 *     - we want to read a secret "a" with labels {color: red} and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a secret "a-us-west" is not present, and since
	 *       it is part of the "strict-for-profiles", we will fail
	 *
	 * </pre>
	 */
	@Test
	void testStrictFive() {

		Secret a = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8))).build();

		Secret aK8s = new SecretBuilder().withNewMetadata().withName("a-k8s").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8))).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(aK8s);
		mockClient.secrets().inNamespace(NAMESPACE).create(a);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		environment.setActiveProfiles("us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));
		profiles.add(new StrictProfile("us-west", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
			() -> data.apply(context));

		Assertions.assertEquals(ex.getMessage(), "source : a-us-west not present in namespace: default");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secret:
	 *             sources:
	 *               - labels:
	 *                   color: red
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a secret "a" with labels {color: red} and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", and a secret "a-us-west" is present, and since
	 *       it is part of the "include-profile-specific-sources", it will be read
	 *
	 * </pre>
	 */
	@Test
	void testStrictSix() {

		Secret a = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8))).build();

		Secret aK8s = new SecretBuilder().withNewMetadata().withName("a-k8s").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-k8s".getBytes(StandardCharsets.UTF_8))).build();

		Secret aUsWest = new SecretBuilder().withNewMetadata().withName("a-us-west").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-us-west".getBytes(StandardCharsets.UTF_8)))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).create(a);
		mockClient.secrets().inNamespace(NAMESPACE).create(aK8s);
		mockClient.secrets().inNamespace(NAMESPACE).create(aUsWest);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s", "us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("us-west", false));
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a.a-k8s.a-us-west.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-k8s");
	}

	/**
	 * <pre>
	 *   spring:
	 *     cloud:
	 *       kubernetes:
	 *         secrets:
	 *            sources:
	 *              - labels:
	 *                   color: red
	 *                strict: false
	 *                strict-for-profiles:
	 *                  - dev
	 *                include-profile-specific-sources: true
	 *
	 *  - we want to read a secret with labels {color: red} in a non-profile specific source, but it does not exist.
	 *  - since it has "strict=false", we will not fail
	 *
	 *  - we do have a sibling source because of {strict-for-profiles : dev} + it matches the initial labels,
	 *    so we will read it.
	 *
	 * </pre>
	 */
	@Test
	void testStrictSeven() {
		Secret aDev = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a-dev").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-dev".getBytes(StandardCharsets.UTF_8))).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(aDev);

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("dev", true));

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, profiles, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a-dev.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-dev");

	}

	/**
	 * <pre>
	 *   spring:
	 *     cloud:
	 *       kubernetes:
	 *         secrets:
	 *            sources:
	 *              - labels:
	 *                   color: red
	 *                strict: false
	 *                strict-for-profiles:
	 *                  - dev
	 *                  - ack
	 *                include-profile-specific-sources: true
	 *
	 *  - there are 4 active profiles : "dev", "ack", "k8s", "prod".
	 *  - there are 3 secrets: "a-dev", "a-ack", "a-k8s".
	 *
	 *  - we want to read a secret with labels {color: red} in a non-profile specific source, but it does not exist.
	 *  - since it has "strict=false", we will not fail
	 *
	 *  - we have 3 siblings for it: "a-dev", "a-ack" and "a-k8s".
	 *  - "a-prod" does not exist, and it's OK, since "k8s" is not part of "strict-for-profiles" and as such is optional.
	 *  - it is important to notice the order in which we read secrets also.
	 *
	 * </pre>
	 */
	@Test
	void testStrictEight() {
		Secret aDev = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a-dev").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-dev".getBytes(StandardCharsets.UTF_8))).build();

		Secret aAck = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a-ack").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-ack".getBytes(StandardCharsets.UTF_8))).build();

		Secret aK8s = new SecretBuilder().withNewMetadata().withLabels(Map.of("color", "red")).withName("a-k8s").endMetadata()
			.addToData("a", Base64.getEncoder().encodeToString("a-k8s".getBytes(StandardCharsets.UTF_8))).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(aDev);
		mockClient.secrets().inNamespace(NAMESPACE).create(aAck);
		mockClient.secrets().inNamespace(NAMESPACE).create(aK8s);

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("dev", true));
		profiles.add(new StrictProfile("ack", true));
		profiles.add(new StrictProfile("k8s", false));

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev", "ack", "k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), true, profiles, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a-ack.a-dev.a-k8s.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-dev");

	}


}
