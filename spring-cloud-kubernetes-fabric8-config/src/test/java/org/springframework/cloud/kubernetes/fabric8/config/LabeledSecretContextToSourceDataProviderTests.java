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
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
class LabeledSecretContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final Map<String, String> PINK_LABEL = Map.of("color", "pink");

	private static final Map<String, String> BLUE_LABEL = Map.of("color", "blue");

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
		new Fabric8SecretsCache().discardAll();
	}

	/**
	 * we have a single secret deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("test-secret").withLabels(LABELS).endMetadata()
				.addToData("secretName", Base64.getEncoder().encodeToString("secretValue".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, LABELS, true, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(redOne).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redTwo).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(blue).create();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, RED_LABEL, true, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(pink).create();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE, BLUE_LABEL, true, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		// different namespace
		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE + "nope", LABELS, true, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		ConfigUtils.Prefix mePrefix = ConfigUtils.findPrefix("me", false, false, "irrelevant");
		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, mePrefix, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(blueSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(anotherBlue).create();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, false);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecretK8s).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DEFAULT, true);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(shapeSecret).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, true);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecretK8s).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(shapeSecret).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(noFit).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecretK8s).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(shapeSecretK8s).create();

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
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

		mockClient.secrets().inNamespace(NAMESPACE).resource(colorSecret).create();

		NormalizedSource normalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, true);
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
	 *     - secret "red" with label "{color:red}"
	 *     - secret "green" with labels "{color:green}"
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {
		Secret red = new SecretBuilder().withNewMetadata().withName("red")
				.withLabels(Collections.singletonMap("color", "red")).endMetadata()
				.addToData("one", Base64.getEncoder().encodeToString("1".getBytes())).build();

		Secret green = new SecretBuilder().withNewMetadata().withName("green").withLabels(Map.of("color", "green"))
				.endMetadata().addToData("two", Base64.getEncoder().encodeToString("2".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(green).create();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redNormalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE,
				environment);
		Fabric8ContextToSourceData redData = new LabeledSecretContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceData().size(), 1);
		Assertions.assertEquals(redSourceData.sourceData().get("red.one"), "1");
		Assertions.assertTrue(output.getAll().contains("Loaded all secrets in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenNormalizedSource = new LabeledSecretNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "green"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE,
				environment);
		Fabric8ContextToSourceData greenData = new LabeledSecretContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceData().size(), 1);
		Assertions.assertEquals(greenSourceData.sourceData().get("green.two"), "2");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

	}

}
