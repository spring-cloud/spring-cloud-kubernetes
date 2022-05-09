package org.springframework.cloud.kubernetes.client.config.boostrap.stubs;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A test bootstrap that takes care to initialize ApiClient _before_ our main bootstrap
 * context; with some stub data already present.
 *
 * @author wind57
 */
@Order(0)
@Configuration
@ConditionalOnProperty("named.secret.with.prefix.stub")
public class NamedSecretWithPrefixConfigurationStub {

	@Bean
	public WireMockServer wireMock() {
		WireMockServer server = new WireMockServer(options().dynamicPort());
		server.start();
		WireMock.configureFor("localhost", server.port());
		return server;
	}

	@Bean
	public ApiClient apiClient(WireMockServer wireMockServer) {
		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
		apiClient.setDebugging(true);
		stubData();
		return apiClient;
	}

	private void stubData() {
		V1Secret one = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("secret-one").withNamespace("spring-k8s")
				.withResourceVersion("1").build())
			.addToData(Collections.singletonMap("one.property", Base64.getEncoder().encode("one".getBytes()))).build();

		V1Secret two = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("secret-two").withNamespace("spring-k8s")
				.withResourceVersion("1").build())
			.addToData(Collections.singletonMap("one.property", Base64.getEncoder().encode("two".getBytes()))).build();

		V1Secret three = new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("secret-three").withNamespace("spring-k8s")
				.withResourceVersion("1").build())
			.addToData(Collections.singletonMap("one.property", Base64.getEncoder().encode("three".getBytes()))).build();

		V1SecretList allConfigMaps = new V1SecretList();
		allConfigMaps.setItems(Arrays.asList(one, two, three));

		// the actual stub for CoreV1Api calls
		WireMock.stubFor(WireMock.get("/api/v1/namespaces/spring-k8s/secrets")
			.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(allConfigMaps))));
	}

}
