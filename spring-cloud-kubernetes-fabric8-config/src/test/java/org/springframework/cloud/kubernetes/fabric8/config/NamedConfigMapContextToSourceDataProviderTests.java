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
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
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
class NamedConfigMapContextToSourceDataProviderTests {

	private static final String NAMESPACE = "default";

	private static KubernetesClient mockClient;

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("some", false, false, "irrelevant");

	private static final Map<String, String> COLOR_REALLY_RED = Map.of("color", "really-red");

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
		new Fabric8ConfigMapsCache().discardAll();
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("blue", NAMESPACE, true, false);
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, false);
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(red).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, true);

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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(red).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redWithProfile).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-profile");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, true);

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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(red).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redWithTaste).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redWithShape).create();

		// add one more profile and specify that we want profile based config maps
		// also append prefix
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("with-taste", "with-shape");
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX, true);

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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("application", NAMESPACE, true, false);
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", wrongNamespace, true, false);
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, false);
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

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new NamedConfigMapNormalizedSource("one", NAMESPACE, true, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.one.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.singletonMap("key", "value"));
	}

	/**
	 * <pre>
	 *     - two configmaps are deployed : "red", "green", in the same namespace.
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 *     - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {

		ConfigMap red = new ConfigMapBuilder().withNewMetadata().withName("red").endMetadata()
				.addToData(COLOR_REALLY_RED).build();

		ConfigMap green = new ConfigMapBuilder().withNewMetadata().withName("green").endMetadata()
				.addToData("taste", "mango").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(red).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(green).create();

		MockEnvironment env = new MockEnvironment();
		NormalizedSource redNormalizedSource = new NamedConfigMapNormalizedSource("red", NAMESPACE, true, PREFIX,
				false);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE, env);
		Fabric8ContextToSourceData redData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceName(), "configmap.red.default");
		Assertions.assertEquals(redSourceData.sourceData().size(), 1);
		Assertions.assertEquals(redSourceData.sourceData().get("some.color"), "really-red");
		Assertions.assertTrue(output.getAll().contains("Loaded all config maps in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenNormalizedSource = new NamedConfigMapNormalizedSource("green", NAMESPACE, true, PREFIX,
				false);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE, env);
		Fabric8ContextToSourceData greenData = new NamedConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceName(), "configmap.green.default");
		Assertions.assertEquals(greenSourceData.sourceData().size(), 1);
		Assertions.assertEquals(greenSourceData.sourceData().get("some.taste"), "mango");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all config maps in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all config maps in namespace");
		Assertions.assertEquals(out.length, 2);

	}

}
