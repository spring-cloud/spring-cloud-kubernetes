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
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
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
	}

	/**
	 * we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		Secret secret = new SecretBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true,
				new LinkedHashSet<>(), false);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(red);
		mockClient.secrets().inNamespace(NAMESPACE).create(blue);
		mockClient.secrets().inNamespace(NAMESPACE).create(yellow);

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true,
				new LinkedHashSet<>(), false);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(pink);

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("blue", NAMESPACE, true,
				new LinkedHashSet<>(), false);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", true,
				new LinkedHashSet<>(), false);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(red);
		mockClient.secrets().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-profile", true));

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, profiles, true);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(red);
		mockClient.secrets().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-profile", true));

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, profiles,
				true);
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

		mockClient.secrets().inNamespace(NAMESPACE).create(red);
		mockClient.secrets().inNamespace(NAMESPACE).create(redWithTaste);
		mockClient.secrets().inNamespace(NAMESPACE).create(redWithShape);

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-taste", "with-shape");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-taste", true));
		profiles.add(new StrictProfile("with-shape", true));

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, profiles,
				true);

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

		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("single-yaml", NAMESPACE, true,
				new LinkedHashSet<>(), false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.single-yaml.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secret:
	 *             sources:
	 *               - name: a
	 *                 strict: false
	 *
	 *     we want to read a secret "a" and it has "strict=false".
	 *     since "a" is not present at all, as a result we get an empty SourceData.
	 *
	 * </pre>
	 */
	@Test
	void testStrictOne() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, new LinkedHashSet<>(),
				false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 0);
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secrets:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *
	 *     - we want to read a secret "a" and it has "strict=true"
	 *     - since "a" is not present at all (but strict=true), we fail
	 * </pre>
	 */
	@Test
	void testStrictTwo() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, new LinkedHashSet<>(),
				true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> data.apply(context));

		Assertions.assertEquals(ex.getMessage(), "secret with name : a not found in namespace: default");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secret:
	 *             sources:
	 *               - name: a
	 *                 strict: false
	 *                 strict-for-profiles:
	 *                   - k8s
	 *
	 *     - we want to read a secret "a" and it has "strict=false"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *
	 *     since "a" is not present at all, as a result we get an empty SourceData.
	 *     "a-k8s" is not even tried to be read.
	 *
	 * </pre>
	 */
	@Test
	void testStrictThree() {
		Secret aK8s = new SecretBuilder().withNewMetadata().withName("a-k8s").endMetadata()
				.addToData("key", Base64.getEncoder().encodeToString("value".getBytes(StandardCharsets.UTF_8))).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(aK8s);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, profiles, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a-k8s.default");
		Assertions.assertEquals(sourceData.sourceData().get("key"), "value");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           secret:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a secret "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a secret "a-us-west" is not present, and since
	 *       it is not part of the "strict-for-profiles", it will be skipped
	 *
	 * </pre>
	 */
	@Test
	void testStrictFour() {

		Secret a = new SecretBuilder().withNewMetadata().withName("a").endMetadata()
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

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                   - us-west
	 *
	 *     - we want to read a secret "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a secret "a-us-west" is not present, and since
	 *       it is part of the "strict-for-profiles", we will fail
	 *
	 * </pre>
	 */
	@Test
	void testStrictFive() {

		Secret a = new SecretBuilder().withNewMetadata().withName("a").endMetadata()
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

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a secret "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a secret "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", and a secret "a-us-west" is present, and since
	 *       it is part of the "include-profile-specific-sources", it will be read
	 *
	 * </pre>
	 */
	@Test
	void testStrictSix() {

		Secret a = new SecretBuilder().withNewMetadata().withName("a").endMetadata()
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
		profiles.add(new StrictProfile("k8s", true));
		profiles.add(new StrictProfile("us-west", false));

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.a.a-k8s.a-us-west.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-us-west");
	}

}
