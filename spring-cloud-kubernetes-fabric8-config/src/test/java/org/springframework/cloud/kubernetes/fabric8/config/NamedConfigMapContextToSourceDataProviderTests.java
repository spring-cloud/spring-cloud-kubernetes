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

import java.util.Collections;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.cloud.kubernetes.commons.config.SourceDataEntriesProcessor;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests only for the happy-path scenarios. All others are tested elsewhere.
 *
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class NamedConfigMapContextToSourceDataProviderTests {

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
		mockClient.configMaps().inNamespace(NAMESPACE).delete();
	}

	/**
	 * we have a single config map deployed. it does not match our query.
	 */
	@Test
	void noMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("blue", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());

	}

	/**
	 * we have a single config map deployed. it matches our query.
	 */
	@Test
	void match() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
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

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		ConfigMap redWithProfile = new ConfigMapBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", "mango").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(red);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
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

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		ConfigMap redWithProfile = new ConfigMapBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", "mango").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(red);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
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

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		ConfigMap redWithTaste = new ConfigMapBuilder().withNewMetadata().withName("red-with-taste").endMetadata()
				.addToData("taste", "mango").build();

		ConfigMap redWithShape = new ConfigMapBuilder().withNewMetadata().withName("red-with-shape").endMetadata()
				.addToData("shape", "round").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(red);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithTaste);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithShape);

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-taste", "with-shape");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-shape.red-with-taste.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 3);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");
		Assertions.assertEquals(sourceData.sourceData().get("some.shape"), "round");

	}

	// when reading config maps and creating normalized sources, we will always be
	// providing a name
	// for the config map; even if one is not provided explicitly.
	@Test
	void matchWithName() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("application").endMetadata()
				.addToData("color", "red").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("application", NAMESPACE, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.application.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "red"));
	}

	/**
	 * NamedSecretContextToSourceDataProvider gets as input a Fabric8ConfigContext. This
	 * context has a namespace as well as a NormalizedSource, that has a namespace too. It
	 * is easy to get confused in code on which namespace to use. This test makes sure
	 * that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("color", "really-red").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		// different namespace
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE + "nope", true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = NamedConfigMapContextToSourceDataProvider
				.of(SourceDataEntriesProcessor::processAllEntries).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "really-red"));
	}

}
