/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class LabeledConfigMapContextToSourceDataProviderTests {

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final Map<String, String> BLUE_LABEL = Map.of("color", "blue");

	private static final Map<String, String> PINK_LABEL = Map.of("color", "pink");

	private static final String NAMESPACE = "default";

	static {
		LABELS.put("label2", "value2");
		LABELS.put("label1", "value1");
	}

	@BeforeAll
	static void setup() {
		WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
		new KubernetesClientConfigMapsCache().discardAll();
	}

	/**
	 * we have a single config map deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleConfigMapMatchAgainstLabels() {

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("test-configmap")
				.withLabels(LABELS).withNamespace(NAMESPACE).build()).addToData("name", "value").build();
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, LABELS, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap redOne = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("red-configmap")
				.withLabels(RED_LABEL).withNamespace(NAMESPACE).build()).addToData("colorOne", "really-red").build();

		V1ConfigMap redTwo = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("red-configmap-again").withLabels(RED_LABEL).withNamespace(NAMESPACE).build())
				.addToData("colorTwo", "really-red-again").build();

		V1ConfigMap blue = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("blue-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("color", "blue").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(redOne).addItemsItem(redTwo)
				.addItemsItem(blue);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, RED_LABEL, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("pink-configmap")
				.withLabels(PINK_LABEL).withNamespace(NAMESPACE).build()).addToData("color", "pink").build();
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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
		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("test-configmap")
				.withLabels(LABELS).withNamespace(NAMESPACE).build()).addToData("name", "value").build();
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		String wrongNamespace = NAMESPACE + "nope";
		NormalizedSource source = new LabeledConfigMapNormalizedSource(wrongNamespace, LABELS, true, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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
		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("blue-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("what-color", "blue-color").build();
		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix mePrefix = ConfigUtils.findPrefix("me", false, false, "irrelevant");
		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true, mePrefix, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("blue-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("first", "blue").build();

		V1ConfigMap two = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("another-blue-configmap").withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build())
				.addToData("second", "blue").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one).addItemsItem(two);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true,
				ConfigUtils.Prefix.DELAYED, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("color-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("one", "1").build();

		V1ConfigMap two = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("color-config-k8s").withNamespace(NAMESPACE).build())
				.addToData("two", "2").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one).addItemsItem(two);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, RED_LABEL, true,
				ConfigUtils.Prefix.DEFAULT, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("color-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("one", "1").build();

		V1ConfigMap two = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("shape-configmap").withNamespace(NAMESPACE).build())
				.addToData("two", "2").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one).addItemsItem(two);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true,
				ConfigUtils.Prefix.DEFAULT, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap one = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("color-configmap")
				.withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build()).addToData("one", "1").build();

		V1ConfigMap two = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("color-configmap-k8s").withLabels(RED_LABEL).withNamespace(NAMESPACE).build())
				.addToData("two", "2").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(one).addItemsItem(two);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true,
				ConfigUtils.Prefix.DELAYED, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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

		V1ConfigMap colorConfigMap = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("color-configmap").withLabels(BLUE_LABEL).withNamespace(NAMESPACE).build())
				.addToData("one", "1").build();

		V1ConfigMap shapeConfigmap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("shape-configmap")
						.withLabels(Map.of("color", "blue", "shape", "round")).withNamespace(NAMESPACE).build())
				.addToData("two", "2").build();

		V1ConfigMap noFit = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder().withName("no-fit")
				.withLabels(Map.of("tag", "no-fit")).withNamespace(NAMESPACE).build()).addToData("three", "3").build();

		V1ConfigMap colorConfigmapK8s = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withName("color-configmap-k8s").withLabels(RED_LABEL).withNamespace(NAMESPACE).build())
				.addToData("four", "4").build();

		V1ConfigMap shapeConfigmapK8s = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("shape-configmap-k8s")
						.withLabels(Map.of("shape", "triangle")).withNamespace(NAMESPACE).build())
				.addToData("five", "5").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(colorConfigMap).addItemsItem(shapeConfigmap)
				.addItemsItem(noFit).addItemsItem(colorConfigmapK8s).addItemsItem(shapeConfigmapK8s);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");

		NormalizedSource source = new LabeledConfigMapNormalizedSource(NAMESPACE, BLUE_LABEL, true,
				ConfigUtils.Prefix.DELAYED, true);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment);

		KubernetesClientContextToSourceData data = new LabeledConfigMapContextToSourceDataProvider().get();
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
	 *     - one configmap is deployed with label {"color", "red"}
	 *     - one configmap is deployed with label {"color", "green"}
	 *
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is retrieved from the cache this time.
	 * </pre>
	 */
	@Test
	void cache(CapturedOutput output) {
		V1ConfigMap red = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withLabels(Map.of("color", "red")).withNamespace(NAMESPACE).withName("red-configmap").build())
				.addToData("color", "red").build();

		V1ConfigMap green = new V1ConfigMapBuilder().withMetadata(new V1ObjectMetaBuilder()
				.withLabels(Map.of("color", "green")).withNamespace(NAMESPACE).withName("green-configmap").build())
				.addToData("color", "green").build();

		V1ConfigMapList configMapList = new V1ConfigMapList().addItemsItem(red).addItemsItem(green);

		stubCall(configMapList);
		CoreV1Api api = new CoreV1Api();

		NormalizedSource redSource = new LabeledConfigMapNormalizedSource(NAMESPACE, Map.of("color", "red"), false,
				ConfigUtils.Prefix.DEFAULT, false);
		KubernetesClientConfigContext redContext = new KubernetesClientConfigContext(api, redSource, NAMESPACE,
				new MockEnvironment());
		KubernetesClientContextToSourceData redData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertEquals(redSourceData.sourceData().size(), 1);
		Assertions.assertEquals(redSourceData.sourceData().get("color"), "red");
		Assertions.assertEquals(redSourceData.sourceName(), "configmap.red-configmap.default");
		Assertions.assertTrue(output.getAll().contains("Loaded all config maps in namespace '" + NAMESPACE + "'"));

		NormalizedSource greenSource = new LabeledConfigMapNormalizedSource(NAMESPACE, Map.of("color", "green"), false,
				ConfigUtils.Prefix.DEFAULT, false);
		KubernetesClientConfigContext greenContext = new KubernetesClientConfigContext(api, greenSource, NAMESPACE,
				new MockEnvironment());
		KubernetesClientContextToSourceData greenData = new LabeledConfigMapContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertEquals(greenSourceData.sourceData().size(), 1);
		Assertions.assertEquals(greenSourceData.sourceData().get("color"), "green");
		Assertions.assertEquals(greenSourceData.sourceName(), "configmap.green-configmap.default");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all config maps in namespace");
		Assertions.assertEquals(out.length, 2);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Loaded \\(from cache\\) all config maps in namespace");
		Assertions.assertEquals(out.length, 2);
	}

	private void stubCall(V1ConfigMapList list) {
		stubFor(get("/api/v1/namespaces/default/configmaps")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(list))));
	}

}
