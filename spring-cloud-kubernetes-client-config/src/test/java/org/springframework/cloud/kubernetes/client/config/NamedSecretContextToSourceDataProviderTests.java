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
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

class NamedSecretContextToSourceDataProviderTests {

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
	 *
	 * /** we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false, "");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}

	/**
	 * we have three secrets deployed. one of them has a name that matches (red), the
	 * other two have different names, thus no match.
	 */
	@Test
	void twoSecretMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build())
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("blue")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build())
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("pink")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false, "");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
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

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("blue", NAMESPACE, false, "");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	@Test
	void namespaceMatch() {

		V1SecretList secretList = new V1SecretList()
				.addItemsItem(
						new V1SecretBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withNamespace(NAMESPACE).withName("red")
										.withResourceVersion("1").build())
								.addToData("color", "really-red".getBytes()).build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", false, "");
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE,
				new MockEnvironment());

		KubernetesClientContextToSourceData data = new NamedSecretContextToSourceDataProvider().get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secret.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));
	}

}
