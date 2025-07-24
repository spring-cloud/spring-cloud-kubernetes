/*
 * Copyright 2012-2024 the original author or authors.
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
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
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class NamedSecretContextToSourceDataProviderNonNamespacedBatchReadTests {

	private static final boolean NAMESPACED_BATCH_READ = false;

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
		new Fabric8SourcesNamespaceBatched().discardSecrets();
	}

	/**
	 * we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		Secret secret = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "really-red"));

	}

	/**
	 * we have three secret deployed. one of them has a name that matches (red), the other
	 * two have different names, thus no match.
	 */
	@Test
	void twoSecretMatchAgainstLabels() {

		Secret red = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		Secret blue = new SecretBuilder().withNewMetadata()
			.withName("blue")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-blue".getBytes()))
			.build();

		Secret yellow = new SecretBuilder().withNewMetadata()
			.withName("yellow")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-yellow".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(blue).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(yellow).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.default");
		Assertions.assertThat(sourceData.sourceData()).hasSize(1);
		Assertions.assertThat(sourceData.sourceData().get("color")).isEqualTo("really-red");

	}

	/**
	 * one secret deployed (pink), does not match our query (blue).
	 */
	@Test
	void testSecretNoMatch() {

		Secret pink = new SecretBuilder().withNewMetadata()
			.withName("pink")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("pink".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(pink).create();

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("blue", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.blue.default");
		Assertions.assertThat(sourceData.sourceData()).isEmpty();
	}

	/**
	 * NamedSecretContextToSourceDataProvider gets as input a Fabric8ConfigContext. This
	 * context has a namespace as well as a NormalizedSource, that has a namespace too. It
	 * is easy to get confused in code on which namespace to use. This test makes sure
	 * that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		Secret secret = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "really-red"));
	}

	/**
	 * we have two secrets deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also.
	 */
	@Test
	void matchIncludeSingleProfile() {

		Secret red = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		Secret redWithProfile = new SecretBuilder().withNewMetadata()
			.withName("red-with-profile")
			.endMetadata()
			.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true,
				ConfigUtils.Prefix.DEFAULT, true, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env,
				NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.red-with-profile.default.with-profile");
		Assertions.assertThat(sourceData.sourceData()).hasSize(2);
		Assertions.assertThat(sourceData.sourceData().get("color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("taste")).isEqualTo("mango");

	}

	/**
	 * we have two secrets deployed. one matches the query name. the other matches the
	 * active profile + name, thus is taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * secrets as well.
	 */
	@Test
	void matchIncludeSingleProfileWithPrefix() {

		Secret red = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		Secret redWithProfile = new SecretBuilder().withNewMetadata()
			.withName("red-with-profile")
			.endMetadata()
			.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env,
				NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.red-with-profile.default");
		Assertions.assertThat(sourceData.sourceData()).hasSize(2);
		Assertions.assertThat(sourceData.sourceData().get("some.color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("some.taste")).isEqualTo("mango");

	}

	/**
	 * we have three secrets deployed. one matches the query name. the other two match the
	 * active profile + name, thus are taken also. This takes into consideration the
	 * prefix, that we explicitly specify. Notice that prefix works for profile based
	 * config maps as well.
	 */
	@Test
	void matchIncludeTwoProfilesWithPrefix() {

		Secret red = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("really-red".getBytes()))
			.build();

		Secret redWithTaste = new SecretBuilder().withNewMetadata()
			.withName("red-with-taste")
			.endMetadata()
			.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes()))
			.build();

		Secret redWithShape = new SecretBuilder().withNewMetadata()
			.withName("red-with-shape")
			.endMetadata()
			.addToData("shape", Base64.getEncoder().encodeToString("round".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithTaste).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(redWithShape).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-taste", "with-shape");
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env,
				NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.red.red-with-shape.red-with-taste.default");

		Assertions.assertThat(sourceData.sourceData()).hasSize(3);
		Assertions.assertThat(sourceData.sourceData().get("some.color")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("some.taste")).isEqualTo("mango");
		Assertions.assertThat(sourceData.sourceData().get("some.shape")).isEqualTo("round");

	}

	/**
	 * <pre>
	 *     - proves that single yaml file gets special treatment
	 * </pre>
	 */
	@Test
	void testSingleYaml() {
		Secret secret = new SecretBuilder().withNewMetadata()
			.withName("single-yaml")
			.endMetadata()
			.addToData("single.yaml", Base64.getEncoder().encodeToString("key: value".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		// different namespace
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("single-yaml", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), NAMESPACED_BATCH_READ);

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.single-yaml.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *     - two secrets are deployed : "red", "green", in the same namespace.
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 *     - we then search for the "green" one, and it is not retrieved from the cache.
	 * </pre>
	 */
	@Test
	void nonCache(CapturedOutput output) {

		Secret red = new SecretBuilder().withNewMetadata()
			.withName("red")
			.endMetadata()
			.addToData("color", Base64.getEncoder().encodeToString("red".getBytes()))
			.build();

		Secret green = new SecretBuilder().withNewMetadata()
			.withName("green")
			.endMetadata()
			.addToData("taste", Base64.getEncoder().encodeToString("mango".getBytes()))
			.build();

		mockClient.secrets().inNamespace(NAMESPACE).resource(red).create();
		mockClient.secrets().inNamespace(NAMESPACE).resource(green).create();

		MockEnvironment env = new MockEnvironment();
		NormalizedSource redNormalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, PREFIX, false);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE, env,
				NAMESPACED_BATCH_READ);
		Fabric8ContextToSourceData redData = new NamedSecretContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertThat(redSourceData.sourceName()).isEqualTo("secret.red.default");
		Assertions.assertThat(redSourceData.sourceData()).hasSize(1);
		Assertions.assertThat(redSourceData.sourceData().get("some.color")).isEqualTo("red");

		Assertions.assertThat(output.getAll()).doesNotContain("Loaded all secrets in namespace '" + NAMESPACE + "'");
		Assertions.assertThat(output.getOut()).contains("Will read individual secrets in namespace");

		NormalizedSource greenNormalizedSource = new NamedSecretNormalizedSource("green", NAMESPACE, true, PREFIX,
				false);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE, env,
				NAMESPACED_BATCH_READ);
		Fabric8ContextToSourceData greenData = new NamedSecretContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertThat(greenSourceData.sourceName()).isEqualTo("secret.green.default");
		Assertions.assertThat(greenSourceData.sourceData()).hasSize(1);
		Assertions.assertThat(greenSourceData.sourceData().get("some.taste")).isEqualTo("mango");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all secrets in namespace");
		Assertions.assertThat(out.length).isEqualTo(1);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Will read individual secrets in namespace");
		Assertions.assertThat(out.length).isEqualTo(3);

	}

}
