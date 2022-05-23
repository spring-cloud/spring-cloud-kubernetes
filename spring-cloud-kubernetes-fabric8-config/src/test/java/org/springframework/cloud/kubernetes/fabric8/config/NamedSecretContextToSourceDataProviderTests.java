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
class NamedSecretContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static KubernetesClient mockClient;

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

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, "");
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
				.addToData("color", Base64.getEncoder().encodeToString("really-yeallow".getBytes())).build();

		mockClient.secrets().inNamespace(NAMESPACE).create(red);
		mockClient.secrets().inNamespace(NAMESPACE).create(blue);
		mockClient.secrets().inNamespace(NAMESPACE).create(yellow);

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE, true, "");
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

		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("blue", NAMESPACE, true, "");
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
		NormalizedSource normalizedSource = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", true, "");
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));
	}

}
