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
import java.util.LinkedHashSet;
import java.util.Map;

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
import org.springframework.cloud.kubernetes.commons.config.StrictProfile;
import org.springframework.cloud.kubernetes.commons.config.StrictSourceNotFoundException;
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

	private static final Map<String, String> COLOR_REALLY_RED = Map.of("color", "really-red");

	private static final LinkedHashSet<StrictProfile> EMPTY = new LinkedHashSet<>();

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
	 * <pre>
	 *     one configmap deployed with name "red"
	 *     we search by name, but for the "blue" one, as such not find it
	 * </pre>
	 */
	@Test
	void noMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("blue", NAMESPACE, true, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());

	}

	/**
	 * <pre>
	 *     one configmap deployed with name "red"
	 *     we search by name, for the "red" one, as such we find it
	 * </pre>
	 */
	@Test
	void match() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), COLOR_REALLY_RED);

	}

	/**
	 * <pre>
	 *     - two configmaps deployed : "red" and "red-with-profile".
	 *     - "red" is matched directly, "red-with-profile" is matched because we have an active profile
	 *       "active-profile"
	 * </pre>
	 */
	@Test
	void matchIncludeSingleProfile() {

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		ConfigMap redWithProfile = new ConfigMapBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", "mango").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(red);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-profile", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, profiles, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-profile.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("taste"), "mango");

	}

	/**
	 * <pre>
	*     - two configmaps deployed : "red" and "red-with-profile".
	*     - "red" is matched directly, "red-with-profile" is matched because we have an active profile
	*       "active-profile"
	*     -  This takes into consideration the prefix, that we explicitly specify.
	*        Notice that prefix works for profile based config maps as well.
	* </pre>
	 */
	@Test
	void matchIncludeSingleProfileWithPrefix() {

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		ConfigMap redWithProfile = new ConfigMapBuilder().withNewMetadata().withName("red-with-profile").endMetadata()
				.addToData("taste", "mango").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(red);
		mockClient.configMaps().inNamespace(NAMESPACE).create(redWithProfile);

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-profile", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, profiles,
				true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-profile.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");

	}

	/**
	 * <pre>
	 *     - three configmaps deployed : "red", "red-with-taste" and "red-with-shape"
	 *     - "red" is matched directly, the other two are matched because of active profiles
	 *     -  This takes into consideration the prefix, that we explicitly specify.
	 *        Notice that prefix works for profile based config maps as well.
	 * </pre>
	 */
	@Test
	void matchIncludeTwoProfilesWithPrefix() {

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

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

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("with-taste", true));
		profiles.add(new StrictProfile("with-shape", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, profiles,
				true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, env);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.red-with-shape.red-with-taste.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 3);
		Assertions.assertEquals(sourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("some.taste"), "mango");
		Assertions.assertEquals(sourceData.sourceData().get("some.shape"), "round");

	}

	/**
	 * <pre>
	 * 		proves that an implicit configmap is going to be generated and read, even if
	 * 	    we did not provide one
	 * </pre>
	 */
	@Test
	void matchWithName() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("application").endMetadata()
				.addToData("color", "red").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("application", NAMESPACE, true, EMPTY,
				false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.application.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "red"));
	}

	/**
	 * <pre>
	 *     - NamedSecretContextToSourceDataProvider gets as input a KubernetesClientConfigContext
	 *     - This context has a namespace as well as a NormalizedSource, that has a namespace too.
	 *     - This test makes sure that we use the proper one.
	 * </pre>
	 */
	@Test
	void namespaceMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", wrongNamespace, true, EMPTY,
				false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("color", "really-red"));
	}

	/**
	 * <pre>
	 *     - proves that single yaml file gets special treatment
	 * </pre>
	 */
	@Test
	void testSingleYaml() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData("single.yaml", "key: value").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *     - one configmap is deployed with name "one"
	 *     - profile is enabled with name "k8s"
	 *
	 *     we assert that the name of the source is "one" and does not contain "one-dev"
	 * </pre>
	 */
	@Test
	void testCorrectNameWithProfile() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("one").endMetadata()
				.addToData("key", "value").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(configMap);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("one", NAMESPACE, true, EMPTY, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.one.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: false
	 *
	 *     we want to read a configmap "a" and it has "strict=false".
	 *     since "a" is not present at all, as a result we get an empty SourceData.
	 *
	 * </pre>
	 */
	@Test
	void testStrictOne() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, EMPTY, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.a.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 0);
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *
	 *     - we want to read a configmap "a" and it has "strict=true"
	 *     - since "a" is not present at all (but strict=true), we fail
	 * </pre>
	 */
	@Test
	void testStrictTwo() {
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, EMPTY, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> data.apply(context));

		Assertions.assertEquals(ex.getMessage(), "configmap with name : a not found in namespace: default");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: false
	 *                 strict-for-profiles:
	 *                   - k8s
	 *
	 *     - we want to read a configmap "a" and it has "strict=false"
	 *     - there is one profile active "k8s" and there is a configmap "a-k8s" that has strict=true
	 *
	 *     since "a" is not present at all, as a result we get an empty SourceData.
	 *     "a-k8s" is not even tried to be read.
	 *
	 * </pre>
	 */
	@Test
	void testStrictThree() {
		ConfigMap aK8s = new ConfigMapBuilder().withNewMetadata().withName("a-k8s").endMetadata()
				.addToData("key", "value").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(aK8s);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, profiles, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.a-k8s.default");
		Assertions.assertEquals(sourceData.sourceData().get("key"), "value");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a configmap "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a configmap "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a configmap "a-us-west" is not present, and since
	 *       it is not part of the "strict-for-profiles", it will be skipped
	 *
	 * </pre>
	 */
	@Test
	void testStrictFour() {

		ConfigMap a = new ConfigMapBuilder().withNewMetadata().withName("a").endMetadata().addToData("a", "a").build();

		ConfigMap aK8s = new ConfigMapBuilder().withNewMetadata().withName("a-k8s").endMetadata()
				.addToData("a", "a-k8s").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(aK8s);
		mockClient.configMaps().inNamespace(NAMESPACE).create(a);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		environment.setActiveProfiles("us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.a.a-k8s.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-k8s");
	}

	/**
	 * <pre>
	 *
	 *     spring:
	 *       cloud:
	 *         kubernetes:
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                   - us-west
	 *
	 *     - we want to read a configmap "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a configmap "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", but a configmap "a-us-west" is not present, and since
	 *       it is part of the "strict-for-profiles", we will fail
	 *
	 * </pre>
	 */
	@Test
	void testStrictFive() {

		ConfigMap a = new ConfigMapBuilder().withNewMetadata().withName("a").endMetadata().addToData("a", "a").build();

		ConfigMap aK8s = new ConfigMapBuilder().withNewMetadata().withName("a-k8s").endMetadata()
				.addToData("a", "a-k8s").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(aK8s);
		mockClient.configMaps().inNamespace(NAMESPACE).create(a);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		environment.setActiveProfiles("us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("k8s", true));
		profiles.add(new StrictProfile("us-west", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
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
	 *           config:
	 *             sources:
	 *               - name: a
	 *                 strict: true
	 *                 strict-for-profiles:
	 *                   - k8s
	 *                 include-profile-specific-sources: true
	 *
	 *     - we want to read a configmap "a" and it has "strict=true"
	 *     - there is one profile active "k8s" and there is a configmap "a-k8s" that has strict=true
	 *     - we also have a profile "us-west", and a configmap "a-us-west" is present, and since
	 *       it is part of the "include-profile-specific-sources", it will be read
	 *
	 * </pre>
	 */
	@Test
	void testStrictSix() {

		ConfigMap a = new ConfigMapBuilder().withNewMetadata().withName("a").endMetadata().addToData("a", "a").build();

		ConfigMap aK8s = new ConfigMapBuilder().withNewMetadata().withName("a-k8s").endMetadata()
				.addToData("a", "a-k8s").build();

		ConfigMap aUsWest = new ConfigMapBuilder().withNewMetadata().withName("a-us-west").endMetadata()
				.addToData("a", "a-us-west").build();

		mockClient.configMaps().inNamespace(NAMESPACE).create(a);
		mockClient.configMaps().inNamespace(NAMESPACE).create(aK8s);
		mockClient.configMaps().inNamespace(NAMESPACE).create(aUsWest);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s", "us-west");

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("us-west", false));
		profiles.add(new StrictProfile("k8s", true));

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("a", NAMESPACE, true, profiles, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.a.a-k8s.a-us-west.default");
		Assertions.assertEquals(sourceData.sourceData().get("a"), "a-k8s");
	}

}
