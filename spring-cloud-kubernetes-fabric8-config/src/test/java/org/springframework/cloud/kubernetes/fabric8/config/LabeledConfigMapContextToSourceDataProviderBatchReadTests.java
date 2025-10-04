/*
 * Copyright 2012-present the original author or authors.
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
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class LabeledConfigMapContextToSourceDataProviderBatchReadTests {

	private static final String NAMESPACE = "default";

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final Map<String, String> PINK_LABEL = Map.of("color", "pink");

	private static final Map<String, String> BLUE_LABEL = Map.of("color", "blue");

	private static KubernetesClient mockClient;

	private static KubernetesMockServer mockServer;

	@BeforeAll
	static void beforeAll() {

		LABELS.put("label2", "value2");
		LABELS.put("label1", "value1");

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
		Fabric8SourcesBatchRead.discardConfigMaps();
	}

	/**
	 * we have a single config map deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleConfigMapMatchAgainstLabels() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName("test-configmap")
			.withLabels(LABELS)
			.endMetadata()
			.addToData("name", "value")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, LABELS, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.test-configmap.default");
		Assertions.assertThat(sourceData.sourceData()).containsExactlyInAnyOrderEntriesOf(Map.of("name", "value"));

	}

	/**
	 * we have three configmaps deployed. two of them have labels that match (color=red),
	 * one does not (color=blue).
	 */
	@Test
	void twoConfigMapsMatchAgainstLabels() {

		ConfigMap redOne = new ConfigMapBuilder().withNewMetadata()
			.withName("red-configmap")
			.withLabels(RED_LABEL)
			.endMetadata()
			.addToData("colorOne", "really-red")
			.build();

		ConfigMap redTwo = new ConfigMapBuilder().withNewMetadata()
			.withName("red-configmap-again")
			.withLabels(RED_LABEL)
			.endMetadata()
			.addToData("colorTwo", "really-red-again")
			.build();

		ConfigMap blue = new ConfigMapBuilder().withNewMetadata()
			.withName("blue-configmap")
			.withLabels(BLUE_LABEL)
			.endMetadata()
			.addToData("color", "blue")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(redOne).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(redTwo).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(blue).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, RED_LABEL, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.red-configmap.red-configmap-again.default");
		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(2);
		Assertions.assertThat(sourceData.sourceData().get("colorOne")).isEqualTo("really-red");
		Assertions.assertThat(sourceData.sourceData().get("colorTwo")).isEqualTo("really-red-again");

	}

	/**
	 * one configmap deployed (pink), does not match our query (blue).
	 */
	@Test
	void configMapNoMatch() {

		ConfigMap pink = new ConfigMapBuilder().withNewMetadata()
			.withName("pink-configmap")
			.withLabels(PINK_LABEL)
			.endMetadata()
			.addToData("color", "pink")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(pink).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.color.default");
		Assertions.assertThat(sourceData.sourceData()).isEmpty();
	}

	/**
	 * LabeledConfigMapContextToSourceDataProvider gets as input a Fabric8ConfigContext.
	 * This context has a namespace as well as a NormalizedSource, that has a namespace
	 * too. It is easy to get confused in code on which namespace to use. This test makes
	 * sure that we use the proper one.
	 */
	@Test
	void namespaceMatch() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName("test-configmap")
			.withLabels(LABELS)
			.endMetadata()
			.addToData("name", "value")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		// different namespace
		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE + "nope", LABELS, true,
				false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.test-configmap.default");
		Assertions.assertThat(sourceData.sourceData()).containsExactlyInAnyOrderEntriesOf(Map.of("name", "value"));
	}

	/**
	 * one configmap with name : "blue-configmap" and labels "color=blue" is deployed. we
	 * search it with the same labels, find it, and assert that name of the SourceData (it
	 * must use its name, not its labels) and values in the SourceData must be prefixed
	 * (since we have provided an explicit prefix).
	 */
	@Test
	void testWithPrefix() {
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName("blue-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("what-color", "blue-color")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

		ConfigUtils.Prefix mePrefix = ConfigUtils.findPrefix("me", false, false, "irrelevant");
		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, mePrefix, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.blue-configmap.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("me.what-color", "blue-color"));
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
		ConfigMap blueConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName("blue-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("first", "blue")
			.build();

		ConfigMap anotherBlue = new ConfigMapBuilder().withNewMetadata()
			.withName("another-blue-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("second", "blue")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(blueConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(anotherBlue).create();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE,
				new MockEnvironment(), ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName())
			.isEqualTo("configmap.another-blue-configmap.blue-configmap.default");

		Map<String, Object> properties = sourceData.sourceData();
		Assertions.assertThat(properties.size()).isEqualTo(2);
		Iterator<String> keys = properties.keySet().iterator();
		String firstKey = keys.next();
		String secondKey = keys.next();

		if (firstKey.contains("first")) {
			Assertions.assertThat(firstKey).isEqualTo("blue-configmap.first");
		}

		Assertions.assertThat(secondKey).isEqualTo("another-blue-configmap.second");
		Assertions.assertThat(properties.get(firstKey)).isEqualTo("blue");
		Assertions.assertThat(properties.get(secondKey)).isEqualTo("blue");
	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "color-configmap-k8s" with no labels. We search by "{color:red}", do not find
	 * anything and thus have an empty SourceData.
	 */
	@Test
	void searchWithLabelsNoConfigmapsFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("one", "1")
			.build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap-k8s")
			.endMetadata()
			.addToData("two", "2")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DEFAULT, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment,
				ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData().isEmpty()).isTrue();
		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.color.default");

	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "shape-configmap" with label: "{shape:round}". We search by "{color:blue}" and find
	 * one configmap.
	 */
	@Test
	void searchWithLabelsOneConfigMapFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("one", "1")
			.build();

		ConfigMap shapeConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("shape-configmap")
			.endMetadata()
			.addToData("two", "2")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmap).create();
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DEFAULT, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment,
				ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(1);
		Assertions.assertThat(sourceData.sourceData().get("one")).isEqualTo("1");
		Assertions.assertThat(sourceData.sourceName()).isEqualTo("configmap.color-configmap.default");

	}

	/**
	 * two configmaps are deployed: "color-configmap" with label: "{color:blue}" and
	 * "color-configmap-k8s" with label: "{color:red}". We search by "{color:blue}" and
	 * find one configmap. Since profiles are enabled, we will also be reading
	 * "color-configmap-k8s", even if its labels do not match provided ones.
	 */
	@Test
	void searchWithLabelsOneConfigMapFoundAndOneFromProfileFound() {
		ConfigMap colorConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("one", "1")
			.build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap-k8s")
			.withLabels(Collections.singletonMap("color", "red"))
			.endMetadata()
			.addToData("two", "2")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment,
				ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		assertThat(sourceData.sourceData().size()).isEqualTo(1);
		assertThat(sourceData.sourceData().get("color-configmap.one")).isEqualTo("1");
		assertThat(sourceData.sourceName()).isEqualTo("configmap.color-configmap.default");

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
	void searchWithLabelsTwoConfigMapsFound() {
		ConfigMap colorConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap")
			.withLabels(Collections.singletonMap("color", "blue"))
			.endMetadata()
			.addToData("one", "1")
			.build();

		ConfigMap shapeConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("shape-configmap")
			.withLabels(Map.of("color", "blue", "shape", "round"))
			.endMetadata()
			.addToData("two", "2")
			.build();

		ConfigMap noFit = new ConfigMapBuilder().withNewMetadata()
			.withName("no-fit")
			.withLabels(Map.of("tag", "no-fit"))
			.endMetadata()
			.addToData("three", "3")
			.build();

		ConfigMap colorConfigmapK8s = new ConfigMapBuilder().withNewMetadata()
			.withName("color-configmap-k8s")
			.withLabels(Map.of("color", "red"))
			.endMetadata()
			.addToData("four", "4")
			.build();

		ConfigMap shapeConfigmapK8s = new ConfigMapBuilder().withNewMetadata()
			.withName("shape-configmap-k8s")
			.withLabels(Map.of("shape", "triangle"))
			.endMetadata()
			.addToData("five", "5")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(noFit).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(colorConfigmapK8s).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(shapeConfigmapK8s).create();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource normalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "blue"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, normalizedSource, NAMESPACE, environment,
				ReadType.BATCH);

		Fabric8ContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		assertThat(sourceData.sourceData().size()).isEqualTo(2);

		assertThat(sourceData.sourceData().get("color-configmap.one")).isEqualTo("1");
		assertThat(sourceData.sourceData().get("shape-configmap.two")).isEqualTo("2");

		assertThat(sourceData.sourceName()).isEqualTo("configmap.color-configmap.shape-configmap.default");

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
	void cache() {
		ConfigMap redConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName("red-configmap")
			.withLabels(Collections.singletonMap("color", "red"))
			.endMetadata()
			.addToData("one", "1")
			.build();

		ConfigMap greenConfigmap = new ConfigMapBuilder().withNewMetadata()
			.withName("green-configmap")
			.withLabels(Map.of("color", "green"))
			.endMetadata()
			.addToData("two", "2")
			.build();

		mockClient.configMaps().inNamespace(NAMESPACE).resource(redConfigMap).create();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(greenConfigmap).create();

		MockEnvironment environment = new MockEnvironment();

		NormalizedSource redNormalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "red"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext redContext = new Fabric8ConfigContext(mockClient, redNormalizedSource, NAMESPACE,
				environment, ReadType.BATCH);
		Fabric8ContextToSourceData redData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertThat(redSourceData.sourceData().size()).isEqualTo(1);

		// delete the configmap, if caching is not present, the test would fail
		mockClient.configMaps().inNamespace(NAMESPACE).withName(greenConfigmap.getMetadata().getName()).delete();

		NormalizedSource greenNormalizedSource = new LabeledConfigMapNormalizedSource(NAMESPACE,
				Collections.singletonMap("color", "green"), true, ConfigUtils.Prefix.DELAYED, true);
		Fabric8ConfigContext greenContext = new Fabric8ConfigContext(mockClient, greenNormalizedSource, NAMESPACE,
				environment, ReadType.BATCH);
		Fabric8ContextToSourceData greenData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertThat(greenSourceData.sourceData().size()).isEqualTo(1);
		Assertions.assertThat(greenSourceData.sourceData().get("green-configmap.two")).isEqualTo("2");

	}

}
