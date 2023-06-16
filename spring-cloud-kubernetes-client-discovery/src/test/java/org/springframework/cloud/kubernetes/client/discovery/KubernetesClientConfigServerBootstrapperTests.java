/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Ryan Baxter
 */
class KubernetesClientConfigServerBootstrapperTests {

	private static WireMockServer wireMockServer;

	private ConfigurableApplicationContext context;

	@BeforeEach
	public void before() throws JsonProcessingException {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		V1ServiceList SERVICE_LIST = new V1ServiceListBuilder()
				.withMetadata(new V1ListMetaBuilder().withResourceVersion("1").build())
				.addToItems(new V1ServiceBuilder()
						.withMetadata(new V1ObjectMetaBuilder().withName("spring-cloud-kubernetes-configserver")
								.withNamespace("default").withResourceVersion("0").addToLabels("beta", "true")
								.addToAnnotations("org.springframework.cloud", "true").withUid("0").build())
						.withSpec(new V1ServiceSpecBuilder().withClusterIP("localhost").withSessionAffinity("None")
								.withType("ClusterIP")
								.addToPorts(new V1ServicePortBuilder().withPort(wireMockServer.port()).withName("http")
										.withProtocol("TCP").withNewTargetPort(wireMockServer.port()).build())
								.build())
						.build())
				.build();

		V1EndpointsList ENDPOINTS_LIST = new V1EndpointsListBuilder()
				.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
				.addToItems(new V1Endpoints()
						.metadata(new V1ObjectMeta().name("spring-cloud-kubernetes-configserver").namespace("default"))
						.addSubsetsItem(
								new V1EndpointSubset()
										.addPortsItem(new CoreV1EndpointPort().port(wireMockServer.port()).name("http"))
										.addAddressesItem(new V1EndpointAddress().hostname("localhost").ip("localhost")
												.targetRef(new V1ObjectReferenceBuilder().withUid("uid1").build()))))
				.build();

		Environment environment = new Environment("test", "default");
		Map<String, Object> properties = new HashMap<>();
		properties.put("hello", "world");
		org.springframework.cloud.config.environment.PropertySource p = new PropertySource("p1", properties);
		environment.add(p);
		ObjectMapper objectMapper = new ObjectMapper();
		stubFor(get("/application/default")
				.willReturn(aResponse().withStatus(200).withBody(objectMapper.writeValueAsString(environment))
						.withHeader("content-type", "application/json")));
		stubFor(get("/api/v1/namespaces/default/endpoints?resourceVersion=0&watch=false")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(ENDPOINTS_LIST))
						.withHeader("content-type", "application/json")));
		stubFor(get("/api/v1/namespaces/default/services?resourceVersion=0&watch=false")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SERVICE_LIST))
						.withHeader("content-type", "application/json")));
		stubFor(get(urlMatching("/api/v1/namespaces/default/services.*.watch=true"))
				.willReturn(aResponse().withStatus(200)));
		stubFor(get(urlMatching("/api/v1/namespaces/default/endpoints.*.watch=true"))
				.willReturn(aResponse().withStatus(200)));
	}

	@AfterEach
	public void after() {
		wireMockServer.stop();
		context.close();
	}

	@Test
	void testBootstrapper() {
		this.context = setup().run();
		verify(getRequestedFor(urlEqualTo("/application/default")));
		assertThat(this.context.getEnvironment().getProperty("hello")).isEqualTo("world");
	}

	SpringApplicationBuilder setup(String... env) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(TestConfig.class)
				.properties(addDefaultEnv(env));
		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port())
				.setReadTimeout(Duration.ZERO).build();
		builder.addBootstrapRegistryInitializer(registry -> registry.register(ApiClient.class, (context) -> apiClient));
		builder.addBootstrapRegistryInitializer(new KubernetesClientConfigServerBootstrapper());
		return builder;
	}

	private String[] addDefaultEnv(String[] env) {
		Set<String> set = new LinkedHashSet<>();
		if (env != null && env.length > 0) {
			set.addAll(Arrays.asList(env));
		}
		set.add("server.port=0");
		set.add("spring.cloud.config.discovery.enabled=true");
		set.add("spring.config.import=optional:configserver:");
		set.add("spring.cloud.config.discovery.service-id=spring-cloud-kubernetes-configserver");
		set.add("spring.cloud.kubernetes.client.namespace=default");
		return set.toArray(new String[0]);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

		@Bean
		public ApiClient apiClient() {
			return new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		}

	}

}
