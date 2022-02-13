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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.commons.config.*;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
class LabeledSecretContextToSourceDataProviderTests {

	private static final Map<String, String> LABELS = Map.of("label1", "value1", "label2", "value2");

	private static final Map<String, String> RED_LABEL = Map.of("color", "red");

	private static final String NAMESPACE = "default";

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
	}

	/**
	 * we have a single secret deployed. it does not match our query.
	 */
	@Test
	void noMatch() {

		V1SecretList secretList = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withLabels(Collections.singletonMap("color", "red"))
						.withNamespace(NAMESPACE)
						.withName("red-secret")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dred")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, false, Collections.singletonMap("color", "blue"));
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = LabeledSecretContextToSourceDataProvider
			.of(LabeledSecretContextToSourceDataProviderTests.Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.color.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());

	}

	/**
	 * we have a single secret deployed. it has two labels and these match against our
	 * queries.
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1SecretList SECRETS_LIST = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withLabels(LABELS)
						.withNamespace(NAMESPACE)
						.withResourceVersion("1")
						.withName("test-secret")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%2Clabel1%3Dvalue1")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, false, LABELS);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = LabeledSecretContextToSourceDataProvider
			.of(LabeledSecretContextToSourceDataProviderTests.Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.test-secret.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}


	/**
	 * we have two secrets deployed. both of them have labels that match (color=red).
	 */
	@Test
	void twoSecretsMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList();
		secretList.addItemsItem(
			new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder()
					.withLabels(RED_LABEL)
					.withNamespace(NAMESPACE)
					.withResourceVersion("1")
					.withName("color-one")
					.build())
				.addToData("colorOne", Base64.getEncoder().encode("really-red-one".getBytes()))
				.build()
			);

		secretList.addItemsItem(
			new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder()
					.withLabels(RED_LABEL)
					.withNamespace(NAMESPACE)
					.withResourceVersion("1")
					.withName("color-two")
					.build())
				.addToData("colorTwo", Base64.getEncoder().encode("really-red-two".getBytes()))
				.build()
		);

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=color%3Dred")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE, false, RED_LABEL);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = LabeledSecretContextToSourceDataProvider
			.of(LabeledSecretContextToSourceDataProviderTests.Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.color-one.color-two.default");
		Assertions.assertEquals(sourceData.sourceData().size(), 2);
		Assertions.assertEquals(sourceData.sourceData().get("colorOne"), "really-red-one");
		Assertions.assertEquals(sourceData.sourceData().get("colorTwo"), "really-red-two");

	}

	@Test
	void namespaceMatch() {
		V1SecretList SECRETS_LIST = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withLabels(LABELS)
						.withNamespace(NAMESPACE)
						.withResourceVersion("1")
						.withName("test-secret")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets?labelSelector=label2%3Dvalue2%2Clabel1%3Dvalue1")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRETS_LIST))));

		NormalizedSource source = new LabeledSecretNormalizedSource(NAMESPACE  + "nope", false, LABELS);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = LabeledSecretContextToSourceDataProvider
			.of(LabeledSecretContextToSourceDataProviderTests.Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.test-secret.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));
	}

	// needed only to allow access to the super methods
	private static final class Dummy extends SecretsPropertySource {

		private Dummy() {
			super(SourceData.emptyRecord("dummy-name"));
		}

		private static String sourceName(String name, String namespace) {
			return getSourceName(name, namespace);
		}

	}

}
