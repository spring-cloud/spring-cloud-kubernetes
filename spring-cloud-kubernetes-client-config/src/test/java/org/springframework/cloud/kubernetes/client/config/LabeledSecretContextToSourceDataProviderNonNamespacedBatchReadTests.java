/*
 * Copyright 2013-present the original author or authors.
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

import java.util.Base64;
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
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
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
class LabeledSecretContextToSourceDataProviderNonNamespacedBatchReadTests {

	private static final boolean NAMESPACED_BATCH_READ = false;

	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final String NAMESPACE = "default";

	@BeforeAll
	static void setup() {

		LABELS.put("label2", "value2");
		LABELS.put("label1", "value1");

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
		new KubernetesClientSourcesBatchRead().discardSecrets();
	}

	@AfterAll
	static void afterAll() {
		WireMock.shutdownServer();
	}

	/**
	 * we have a single secret deployed. it does not match our query.
	 */
	@Test
	void noMatch() {

		V1Secret red = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Collections.singletonMap("color", "red"))
				.withNamespace(NAMESPACE)
				.withName("red-secret")
				.build())
			.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
			.build();
		V1SecretList secretList = new V1SecretList().addItemsItem(red);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dred");
		stubCall(new V1SecretList(), "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();

		// blue does not match red
		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE,
			Collections.singletonMap("color", "blue"), false, ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.color.default");
		Assertions.assertThat(sourceData.sourceData()).isEmpty();

	}

	/**
	 * we have a single secret deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1Secret red = new V1SecretBuilder().withMetadata(
				new V1ObjectMetaBuilder().withLabels(LABELS).withNamespace(NAMESPACE).withName("test-secret").build())
			.addToData("color", "really-red".getBytes())
			.build();
		V1SecretList secretList = new V1SecretList().addItemsItem(red);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%26label1%3Dvalue1");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, LABELS,
			false, ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.test-secret.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "really-red"));

	}

	/**
	 * we have two secrets deployed. both of them have labels that match (color=red).
	 */
	@Test
	void twoSecretsMatchAgainstLabels() {

		V1Secret one = new V1SecretBuilder().withMetadata(
				new V1ObjectMetaBuilder().withLabels(RED_LABEL).withNamespace(NAMESPACE).withName("color-one").build())
			.addToData("colorOne", "really-red-one".getBytes())
			.build();

		V1Secret two = new V1SecretBuilder().withMetadata(
				new V1ObjectMetaBuilder().withLabels(RED_LABEL).withNamespace(NAMESPACE).withName("color-two").build())
			.addToData("colorTwo", "really-red-two".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(one).addItemsItem(two);
		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dred");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, RED_LABEL,
			false, ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.color-one.color-two.default");
		Assertions.assertThat(sourceData.sourceData().size()).isEqualTo(2);
		Assertions.assertThat(sourceData.sourceData().get("colorOne")).isEqualTo("really-red-one");
		Assertions.assertThat(sourceData.sourceData().get("colorTwo")).isEqualTo("really-red-two");

	}

	@Test
	void namespaceMatch() {
		V1Secret one = new V1SecretBuilder().withMetadata(
				new V1ObjectMetaBuilder().withLabels(LABELS).withNamespace(NAMESPACE).withName("test-secret").build())
			.addToData("color", "really-red".getBytes())
			.build();
		V1SecretList secretList = new V1SecretList().addItemsItem(one);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%26label1%3Dvalue1");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE + "nope", LABELS,
			false, ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.test-secret.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("color", "really-red"));
	}

	/**
	 * one secret with name : "blue-secret" and labels "color=blue" is deployed. we search
	 * it with the same labels, find it, and assert that name of the SourceData (it must
	 * use its name, not its labels) and values in the SourceData must be prefixed (since
	 * we have provided an explicit prefix).
	 */
	@Test
	void testWithPrefix() {

		V1Secret one = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("blue-secret")
				.build())
			.addToData("what-color", "blue-color".getBytes())
			.build();
		V1SecretList secretList = new V1SecretList().addItemsItem(one);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("me", false, false, null);
		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false, prefix);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.blue-secret.default");
		Assertions.assertThat(sourceData.sourceData())
			.containsExactlyInAnyOrderEntriesOf(Map.of("me.what-color", "blue-color"));
	}

	/**
	 * two secrets are deployed (name:blue-secret, name:another-blue-secret) and labels
	 * "color=blue" (on both). we search with the same labels, find them, and assert that
	 * name of the SourceData (it must use its name, not its labels) and values in the
	 * SourceData must be prefixed (since we have provided a delayed prefix).
	 *
	 * Also notice that the prefix is made up from both secret names.
	 *
	 */
	@Test
	void testTwoSecretsWithPrefix() {

		V1Secret one = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("blue-secret")
				.build())
			.addToData("first", "blue".getBytes())
			.build();

		V1Secret two = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("another-blue-secret")
				.build())
			.addToData("second", "blue".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(one).addItemsItem(two);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
			ConfigUtils.Prefix.DELAYED);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		// maps don't have a defined order, so assert components separately
		Assertions.assertThat(sourceData.sourceName().length()).isEqualTo(46);
		Assertions.assertThat(sourceData.sourceName()).contains("secret");
		Assertions.assertThat(sourceData.sourceName()).contains("blue-secret");
		Assertions.assertThat(sourceData.sourceName()).contains("another-blue-secret");
		Assertions.assertThat(sourceData.sourceName()).contains("default");

		Map<String, Object> properties = sourceData.sourceData();
		Assertions.assertThat(properties).hasSize(2);
		Iterator<String> keys = properties.keySet().iterator();
		String firstKey = keys.next();
		String secondKey = keys.next();

		if (firstKey.contains("first")) {
			Assertions.assertThat(firstKey).isEqualTo("blue-secret.first");
		}

		Assertions.assertThat(secondKey).isEqualTo("another-blue-secret.second");
		Assertions.assertThat(properties.get(firstKey)).isEqualTo("blue");
		Assertions.assertThat(properties.get(secondKey)).isEqualTo("blue");
	}

	/**
	 * two secrets are deployed: secret "color-secret" with label: "{color:blue}" and
	 * "shape-secret" with label: "{shape:round}". We search by "{color:blue}" and find
	 * one secret.
	 */
	@Test
	void searchWithLabelsOneSecretFound() {

		V1Secret colorSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("color-secret")
				.build())
			.addToData("one", "1".getBytes())
			.build();

		V1Secret shapeSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("shape", "round"))
				.withNamespace(NAMESPACE)
				.withName("shape-secret")
				.build())
			.addToData("two", "2".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(colorSecret).addItemsItem(shapeSecret);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
			ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData()).hasSize(1);
		Assertions.assertThat(sourceData.sourceData().get("one")).isEqualTo("1");
		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.color-secret.default");

	}

	/**
	 * two secrets are deployed: secret "color-secret" with label: "{color:blue}" and
	 * "color-secret-k8s" with label: "{color:blue}". We search by "{color:blue}" and find
	 * both.
	 */
	@Test
	void searchWithLabelsOneSecretFoundAndOneFromProfileFound() {

		V1Secret colorSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("blue-ocean-secret")
				.build())
			.addToData("one", "1".getBytes())
			.build();

		V1Secret shapeSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("blue-sky-secret")
				.build())
			.addToData("two", "2".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(colorSecret).addItemsItem(shapeSecret);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
			ConfigUtils.Prefix.DELAYED);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
			false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData()).hasSize(2);
		Assertions.assertThat(sourceData.sourceData().get("blue-ocean-secret.one")).isEqualTo("1");
		Assertions.assertThat(sourceData.sourceData().get("blue-sky-secret.two")).isEqualTo("2");
		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.blue-ocean-secret.blue-sky-secret.default");

	}

	/**
	 * <pre>
	 *     - secret "color-secret" with label "{color:blue}"
	 *     - secret "shape-secret" with labels "{color:blue, shape:round}"
	 *     - secret "no-fit" with labels "{tag:no-fit}"
	 *     - secret "color-secret-k8s" with label "{color:red}"
	 *     - secret "shape-secret-k8s" with label "{shape:triangle}"
	 * </pre>
	 */
	@Test
	void searchWithLabelsTwoSecretsFoundAndOneFromProfileFound() {

		V1Secret colorSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("color-secret")
				.build())
			.addToData("one", "1".getBytes())
			.build();

		V1Secret shapeSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue", "shape", "round"))
				.withNamespace(NAMESPACE)
				.withName("shape-secret")
				.build())
			.addToData("two", "2".getBytes())
			.build();

		V1Secret noFit = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("tag", "no-fit"))
				.withNamespace(NAMESPACE)
				.withName("no-fit")
				.build())
			.addToData("three", "3".getBytes())
			.build();

		V1Secret colorSecretK8s = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("color-secret-k8s")
				.build())
			.addToData("four", "4".getBytes())
			.build();

		V1Secret shapeSecretK8s = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("shape-secret-k8s")
				.build())
			.addToData("five", "5".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(colorSecret)
			.addItemsItem(shapeSecret)
			.addItemsItem(noFit)
			.addItemsItem(colorSecretK8s)
			.addItemsItem(shapeSecretK8s);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();
		MockEnvironment environment = new MockEnvironment();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
			ConfigUtils.Prefix.DELAYED);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, environment,
			false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData()).hasSize(4);
		Assertions.assertThat(sourceData.sourceData().get("color-secret.one")).isEqualTo("1");
		Assertions.assertThat(sourceData.sourceData().get("shape-secret.two")).isEqualTo("2");
		Assertions.assertThat(sourceData.sourceData().get("color-secret-k8s.four")).isEqualTo("4");
		Assertions.assertThat(sourceData.sourceData().get("shape-secret-k8s.five")).isEqualTo("5");

		Assertions.assertThat(sourceData.sourceName())
			.isEqualTo("secret.color-secret.color-secret-k8s.shape-secret.shape-secret-k8s.default");

	}

	/**
	 * yaml/properties gets special treatment
	 */
	@Test
	void testYaml() {
		V1Secret colorSecret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "blue"))
				.withNamespace(NAMESPACE)
				.withName("color-secret")
				.build())
			.addToData("test.yaml", "color: blue".getBytes())
			.build();

		V1SecretList secretList = new V1SecretList().addItemsItem(colorSecret);

		stubCall(secretList, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dblue");
		CoreV1Api api = new CoreV1Api();

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "blue"), false,
			ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);

		KubernetesClientContextToSourceData data = new LabeledSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertThat(sourceData.sourceData()).hasSize(1);
		Assertions.assertThat(sourceData.sourceData().get("color")).isEqualTo("blue");
		Assertions.assertThat(sourceData.sourceName()).isEqualTo("secret.color-secret.default");
	}

	/**
	 * <pre>
	 *     - one secret is deployed with label {"color", "red"}
	 *     - one secret is deployed with label {"color", "green"}
	 *
	 *     - we first search for "red" and find it, and it is retrieved from the cluster via the client.
	 * 	   - we then search for the "green" one, and it is not retrieved from the cache.
	 * </pre>
	 */
	@Test
	void nonCache(CapturedOutput output) {
		V1Secret red = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "red"))
				.withNamespace(NAMESPACE)
				.withName("red")
				.build())
			.addToData("color", "red".getBytes())
			.build();

		V1Secret green = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of("color", "green"))
				.withNamespace(NAMESPACE)
				.withName("green")
				.build())
			.addToData("color", "green".getBytes())
			.build();

		V1SecretList secretListRed = new V1SecretList().addItemsItem(red);
		stubCall(secretListRed, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dred");

		V1SecretList secretListGreen = new V1SecretList().addItemsItem(green);
		stubCall(secretListGreen, "/api/v1/namespaces/default/secrets?labelSelector=color%3Dgreen");

		CoreV1Api api = new CoreV1Api();

		NormalizedSource redSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "red"), false,
			ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext redContext = new KubernetesClientConfigContext(api, redSource, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);
		KubernetesClientContextToSourceData redData = new LabeledSecretContextToSourceDataProvider().get();
		SourceData redSourceData = redData.apply(redContext);

		Assertions.assertThat(redSourceData.sourceData()).hasSize(1);
		Assertions.assertThat(redSourceData.sourceData().get("color")).isEqualTo("red");
		Assertions.assertThat(redSourceData.sourceName()).isEqualTo("secret.red.default");

		Assertions.assertThat(output.getAll()).doesNotContain("Loaded all secrets in namespace '" + NAMESPACE + "'");
		Assertions.assertThat(output.getAll()).contains("Will read individual secrets in namespace");

		NormalizedSource greenSource = new LabeledSecretNormalizedSource(NAMESPACE, Map.of("color", "green"), false,
			ConfigUtils.Prefix.DEFAULT);
		KubernetesClientConfigContext greenContext = new KubernetesClientConfigContext(api, greenSource, NAMESPACE,
			new MockEnvironment(), false, NAMESPACED_BATCH_READ);
		KubernetesClientContextToSourceData greenData = new LabeledSecretContextToSourceDataProvider().get();
		SourceData greenSourceData = greenData.apply(greenContext);

		Assertions.assertThat(greenSourceData.sourceData()).hasSize(1);
		Assertions.assertThat(greenSourceData.sourceData().get("color")).isEqualTo("green");
		Assertions.assertThat(greenSourceData.sourceName()).isEqualTo("secret.green.default");

		// meaning there is a single entry with such a log statement
		String[] out = output.getAll().split("Loaded all secrets in namespace");
		Assertions.assertThat(out.length).isEqualTo(1);

		// meaning that the second read was done from the cache
		out = output.getAll().split("Will read individual secrets in namespace");
		Assertions.assertThat(out.length).isEqualTo(3);
	}

	private void stubCall(V1SecretList configMapList, String path) {
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(configMapList))));
	}

}
