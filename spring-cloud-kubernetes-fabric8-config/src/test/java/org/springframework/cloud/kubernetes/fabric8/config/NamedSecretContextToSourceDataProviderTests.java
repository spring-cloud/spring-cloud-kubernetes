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
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
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
class NamedSecretContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static KubernetesClient mockClient;

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("some", false, false, "irrelevant");

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
	 * we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}

	/**
	 * we have three secret deployed. one of them has a name that matches (red), the other
	 * two have different names, thus no match.
	 */
	@Test
	void twoSecretMatchAgainstLabels() {

		Secret red = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		Secret blue = new SecretBuilder().withNewMetadata().withName("blue").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-blue".getBytes())).build();

		Secret yellow = new SecretBuilder().withNewMetadata().withName("yellow").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-yellow".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(blue).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(yellow).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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

		Secret pink = new SecretBuilder().withNewMetadata().withName("pink").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("pink".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(pink).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("blue", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	/**
	 * NamedSecretContextToSourceDataProvider gets as input a Fabric8ConfigContext. This
	 * context has a namespace as well as a NormalizedSource, that has a namespace too. It
	 * is easy to get confused in code on which namespace to use. This test makes sure
	 * that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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

		Secret red = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		Secret redWithProfile = new SecretBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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

		Secret red = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		Secret redWithProfile = new SecretBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.red-with-profile.default");
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

		Secret red = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		Secret redWithTaste = new SecretBuilder().withNewMetadata().withName("red-with-taste").endMetadata()
				.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes())).build();

		Secret redWithShape = new SecretBuilder().withNewMetadata().withName("red-with-shape").endMetadata()
				.addToData("shape", Base64.getEncoder().encodeToString("round".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithTaste).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithShape).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-taste", "with-shape");
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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
		Secret secret = new SecretBuilder().withNewMetadata().withName("single-yaml").endMetadata()
				.addToData("single.yaml", Base64.getEncoder().encodeToString("key: value".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("single-yaml", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.single-yaml.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *     - two secrets are deployed : "red", "green", in the same namespace.
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 *     - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {

		Secret red = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("red".getBytes())).build();

		Secret green = new SecretBuilder().withNewMetadata().withName("green").endMetadata()
				.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(green).create();

		MockEnvironment env = new MockEnvironment();
		NormalizedSource redNormalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, false);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE, env);
		Fabric8ContextToSourceData redData = new NamedSecretContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(redSourceData.sourceData().size(), 1);
		Assertions.assertEquals(redSourceData.sourceData().get("some.color"), "red");
		Assertions.assertTrue(output.getAll().contains("Loaded all secrets in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenNormalizedSource = new NamedSecretNormalizedSource("green", NAMESPACE, true, PREFIX,
				false);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE, env);
		Fabric8ContextToSourceData greenData = new NamedSecretContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceName(), "secret.green.default");
		Assertions.assertEquals(greenSourceData.sourceData().size(), 1);
		Assertions.assertEquals(greenSourceData.sourceData().get("some.taste"), "mango");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all secrets in namespace");
		Assertions.assertEquals(out.length, 2);

	}

}
