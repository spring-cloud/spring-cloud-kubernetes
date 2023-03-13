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
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import org.springframework.cloud.kubernetes.commons.config.LabeledConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class LabeledConfigMapContextToSourceDataProviderTests {

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
		mockClient.configMaps().inNamespace(NAMESPACE).delete();
		new Fabric8ConfigMapsCache().discardAll();
	}

	/**
	 * we have a single config map deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleConfigMapMatchAgainstLabels() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("test-configmap").withLabels(LABELS)
				.endMetadata().addToData("name", "value").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, LABELS, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("configmap.test-configmap.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("name", "value"), sourceData.sourceData());

	}

	/**
	 * we have three configmaps deployed. two of them have labels that match (color=red),
	 * one does not (color=blue).
	 */
	@Test
	void twoConfigMapsMatchAgainstLabels() {

		ConfigMap redOne = new ConfigMapBuilder().withNewMetadata().withName("red-configmap").withLabels(RED_LABEL)
				.endMetadata().addToData("colorOne", "really-red").build();

		ConfigMap redTwo = new ConfigMapBuilder().withNewMetadata().withName("red-configmap-again")
				.withLabels(RED_LABEL).endMetadata().addToData("colorTwo", "really-red-again").build();

		ConfigMap blue = new ConfigMapBuilder().withNewMetadata().withName("blue-configmap").withLabels(BLUE_LABEL)
				.endMetadata().addToData("color", "blue").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(redOne).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redTwo).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(blue).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, RED_LABEL, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.red-configmap.red-configmap-again.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("colorOne"), "really-red");
		Assertions.assertEquals(sourceData.sourceData().get("colorTwo"), "really-red-again");

	}

	/**
	 * one configmap deployed (pink), does not match our query (blue).
	 */
	@Test
	void configMapNoMatch() {

		ConfigMap pink = new ConfigMapBuilder().withNewMetadata().withName("pink-configmap").withLabels(PINK_LABEL)
				.endMetadata().addToData("color", "pink").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(pink).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.color.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	/**
	 * LabeledConfigMapContextToSourceDataProvider gets as input a Fabric8ConfigContext.
	 * This context has a namespace as well as a NormalizedSource, that has a namespace
	 * too. It is easy to get confused in code on which namespace to use. This test makes
	 * sure that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("test-configmap").withLabels(LABELS)
				.endMetadata().addToData("name", "value").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		// different namespace
		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE + "nope", LABELS, true,
				false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("configmap.test-configmap.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("name", "value"), sourceData.sourceData());
	}

	/**
	 * one configmap with name : "blue-configmap" and labels "color=blue" is deployed. we
	 * search it with the same labels, find it, and assert that name of the SourceData (it
	 * must use its name, not its labels) and values in the SourceData must be prefixed
	 * (since we have provided an explicit prefix).
	 */
	@Test
	void testWithPrefix() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("blue-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata()
				.addToData("what-color", "blue-color").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		ConfigUtils.Prefix mePrefix = ConfigUtils.findPrefix("me", false, false, "irrelevant");
		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, mePrefix, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals("configmap.blue-configmap.default", sourceData.sourceName());
		Assertions.assertEquals(Map.of("me.what-color", "blue-color"), sourceData.sourceData());
	}

	/**
	 * two configmaps are deployed (name:blue-configmap, name:another-blue-configmap) and
	 * labels "color=blue" (on both). we search with the same labels, find them, and
	 * assert that name of the SourceData (it must use its name, not its labels) and
	 * values in the SourceData must be prefixed (since we have provided a delayed
	 * prefix).
	 *
	 * Also notice that the prefix is made up from both configmap names.
	 *
	 */
	@Test
	void testTwoConfigmapsWithPrefix() {
		ConfigMap blueConfigMap = new ConfigMapBuilder().withNewMetadata().withName("blue-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("first", "blue").build();

		ConfigMap anotherBlue = new ConfigMapBuilder().withNewMetadata().withName("another-blue-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("second", "blue")
				.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(blueConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(anotherBlue).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment());

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "configmap.another-blue-configmap.blue-configmap.default");

		Map<String, Object> properties = sourceData.sourceData();
		Assertions.assertEquals(2, properties.size());
		Iterator<String> keys = properties.keySet().iterator();
		String firstKey = keys.next();
		String secondKey = keys.next();

		if (firstKey.contains("first")) {
			Assertions.assertEquals(firstKey, "another-blue-configmap.blue-configmap.first");
		}

		Assertions.assertEquals(secondKey, "another-blue-configmap.blue-configmap.second");
		Assertions.assertEquals(properties.get(firstKey), "blue");
		Assertions.assertEquals(properties.get(secondKey), "blue");
	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "color-configmap-k8s" with no labels. We search by "{color:red}", do not find
	 * anything and thus have an empty SourceData. profile based sources are enabled, but
	 * it has no effect.
	 */
	@Test
	void searchWithLabelsNoConfigmapsFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata().withName("color-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("one", "1").build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata().withName("color-configmap-k8s")
				.endMetadata().addToData("two", "2").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DEFAULT, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertTrue(sourceData.sourceData().isEmpty());
		Assertions.assertEquals(sourceData.sourceName(), "configmap.color.default");

	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "shape-configmap" with label: "{shape:round}". We search by "{color:blue}" and find
	 * one configmap. profile based sources are enabled, but it has no effect.
	 */
	@Test
	void searchWithLabelsOneConfigMapFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata().withName("color-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("one", "1").build();

		ConfigMap shapeConfigmap = new ConfigMapBuilder().withNewMetadata().withName("shape-configmap").endMetadata()
				.addToData("two", "2").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmap).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 1);
		Assertions.assertEquals(sourceData.sourceData().get("one"), "1");
		Assertions.assertEquals(sourceData.sourceName(), "configmap.color-configmap.default");

	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "color-configmap-k8s" with label: "{color:red}". We search by "{color:blue}" and
	 * find one configmap. Since profiles are enabled, we will also be reading
	 * "color-configmap-k8s", even if its labels do not match provided ones.
	 */
	@Test
	void searchWithLabelsOneConfigMapFoundAndOneFromProfileFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata().withName("color-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("one", "1").build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata().withName("color-configmap-k8s")
				.withLabels(Collections.singletonMap("color", "red")).endMetadata().addToData("two", "2").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("color-configmap.color-configmap-k8s.one"), "1");
		Assertions.assertEquals(sourceData.sourceData().get("color-configmap.color-configmap-k8s.two"), "2");
		Assertions.assertEquals(sourceData.sourceName(), "configmap.color-configmap.color-configmap-k8s.default");

	}

	/**
	 * <pre>
	 *     - configmap "color-configmap" with label "{color:blue}"
	 *     - configmap "shape-configmap" with labels "{color:blue, shape:round}"
	 *     - configmap "no-fit" with labels "{tag:no-fit}"
	 *     - configmap "color-configmap-k8s" with label "{color:red}"
	 *     - configmap "shape-configmap-k8s" with label "{shape:triangle}"
	 * </pre>
	 */
	@Test
	void searchWithLabelsTwoConfigMapsFoundAndOneFromProfileFound() {
		ConfigMap colorConfigMap = new ConfigMapBuilder().withNewMetadata().withName("color-configmap")
				.withLabels(Collections.singletonMap("color", "blue")).endMetadata().addToData("one", "1").build();

		ConfigMap shapeConfigmap = new ConfigMapBuilder().withNewMetadata().withName("shape-configmap")
				.withLabels(Map.of("color", "blue", "shape", "round")).endMetadata().addToData("two", "2").build();

		ConfigMap noFit = new ConfigMapBuilder().withNewMetadata().withName("no-fit")
				.withLabels(Map.of("tag", "no-fit")).endMetadata().addToData("three", "3").build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata().withName("color-configmap-k8s")
				.withLabels(Map.of("color", "red")).endMetadata().addToData("four", "4").build();

		ConfigMap shapeConfigmapK8s = new ConfigMapBuilder().withNewMetadata().withName("shape-configmap-k8s")
				.withLabels(Map.of("shape", "triangle")).endMetadata().addToData("five", "5").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(noFit).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmapK8s).create();

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceData().size(), 4);
		Assertions.assertEquals(sourceData.sourceData()
				.get("color-configmap.color-configmap-k8s.shape-configmap.shape-configmap-k8s.one"), "1");
		Assertions.assertEquals(sourceData.sourceData()
				.get("color-configmap.color-configmap-k8s.shape-configmap.shape-configmap-k8s.two"), "2");
		Assertions.assertEquals(sourceData.sourceData()
				.get("color-configmap.color-configmap-k8s.shape-configmap.shape-configmap-k8s.four"), "4");
		Assertions.assertEquals(sourceData.sourceData()
				.get("color-configmap.color-configmap-k8s.shape-configmap.shape-configmap-k8s.five"), "5");

		Assertions.assertEquals(sourceData.sourceName(),
				"configmap.color-configmap.color-configmap-k8s.shape-configmap.shape-configmap-k8s.default");

	}

	/**
	 * <pre>
	 *     - configmap "red-configmap" with label "{color:red}"
	 *     - configmap "green-configmap" with labels "{color:green}"
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {
		ConfigMap redConfigMap = new ConfigMapBuilder().withNewMetadata().withName("red-configmap")
				.withLabels(Collections.singletonMap("color", "red")).endMetadata().addToData("one", "1").build();

		ConfigMap greenConfigmap = new ConfigMapBuilder().withNewMetadata().withName("green-configmap")
				.withLabels(Map.of("color", "green")).endMetadata().addToData("two", "2").build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(redConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(greenConfigmap).create();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redNormalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE,
				environment);
		Fabric8ContextToSourceData redData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceData().size(), 1);
		Assertions.assertEquals(redSourceData.sourceData().get("red-configmap.one"), "1");
		Assertions.assertTrue(output.getAll().contains("Loaded all config maps in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenNormalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "green"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE,
				environment);
		Fabric8ContextToSourceData greenData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceData().size(), 1);
		Assertions.assertEquals(greenSourceData.sourceData().get("green-configmap.two"), "2");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all config maps in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all config maps in namespace");
		Assertions.assertEquals(out.length, 2);

	}

}
