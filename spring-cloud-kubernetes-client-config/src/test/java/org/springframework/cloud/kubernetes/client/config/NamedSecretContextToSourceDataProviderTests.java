package org.springframework.cloud.kubernetes.client.config;

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
import org.springframework.cloud.kubernetes.commons.config.*;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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

	/**
	 * we have a single secret deployed. it matched the name in our queries
	 */
	@Test
	void singleSecretMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withNamespace(NAMESPACE)
						.withName("red")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedSecretContextToSourceDataProvider
			.of(Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.red.default");
		Assertions.assertEquals(sourceData.sourceData(), Map.of("color", "really-red"));

	}

	/**
	 * we have three secrets deployed. one of them has a name that matches (red), the other
	 * two have different names, thus no match.
	 */
	@Test
	void twoSecretMatchAgainstLabels() {

		V1SecretList secretList = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withNamespace(NAMESPACE)
						.withName("red")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build())
			.addItemsItem(
			new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder()
					.withNamespace(NAMESPACE)
					.withName("blue")
					.withResourceVersion("1")
					.build())
				.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
				.build())
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withNamespace(NAMESPACE)
						.withName("pink")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedSecretContextToSourceDataProvider
			.of(Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.red.default");
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
					.withMetadata(new V1ObjectMetaBuilder()
						.withNamespace(NAMESPACE)
						.withName("red")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("blue", NAMESPACE, false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedSecretContextToSourceDataProvider
			.of(Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.blue.default");
		Assertions.assertEquals(sourceData.sourceData(), Collections.emptyMap());
	}

	@Test
	void namespaceMatch() {

		V1SecretList secretList = new V1SecretList()
			.addItemsItem(
				new V1SecretBuilder()
					.withMetadata(new V1ObjectMetaBuilder()
						.withNamespace(NAMESPACE)
						.withName("red")
						.withResourceVersion("1")
						.build())
					.addToData("color", Base64.getEncoder().encode("really-red".getBytes()))
					.build());

		CoreV1Api api = new CoreV1Api();
		stubFor(get("/api/v1/namespaces/default/secrets")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

		// blue does not match red
		NormalizedSource source = new NamedSecretNormalizedSource("red", NAMESPACE + "nope", false);
		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, source, NAMESPACE, new MockEnvironment());

		KubernetesClientContextToSourceData data = NamedSecretContextToSourceDataProvider
			.of(Dummy::sourceName).get();
		SourceData sourceData = data.apply(context);

		Assertions.assertEquals(sourceData.sourceName(), "secrets.red.default");
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
