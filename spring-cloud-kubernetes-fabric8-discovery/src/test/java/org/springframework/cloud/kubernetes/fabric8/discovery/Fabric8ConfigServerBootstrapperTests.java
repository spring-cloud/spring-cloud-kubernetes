/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wiremock.com.fasterxml.jackson.core.JsonProcessingException;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.ConfigurableApplicationContext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Ryan Baxter
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ConfigServerBootstrapperTests {

	private static WireMockServer wireMockServer;

	private KubernetesClient mockClient;

	private ConfigurableApplicationContext context;

	@BeforeEach
	public void before() throws JsonProcessingException {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("spring-cloud-kubernetes-configserver")
				.withNamespace("test").withLabels(new HashMap<>()).endMetadata().addNewSubset().addNewAddress()
				.withHostname("localhost").withIp("localhost").withNewTargetRef().withUid("10").endTargetRef()
				.endAddress().addNewPort("http", "http_tcp", wireMockServer.port(), "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		Service service = new ServiceBuilder().withNewMetadata().withName("spring-cloud-kubernetes-configserver")
				.withNamespace("test").withLabels(new HashMap<>()).endMetadata()
				.withSpec(new ServiceSpecBuilder().withType("NodePort").build()).build();

		mockClient.services().inNamespace("test").create(service);

		Environment environment = new Environment("test", "default");
		Map<String, Object> properties = new HashMap<>();
		properties.put("hello", "world");
		org.springframework.cloud.config.environment.PropertySource p = new PropertySource("p1", properties);
		environment.add(p);
		ObjectMapper objectMapper = new ObjectMapper();
		stubFor(get("/application/default")
				.willReturn(aResponse().withStatus(200).withBody(objectMapper.writeValueAsString(environment))
						.withHeader("content-type", "application/json")));
	}

	@AfterEach
	public void after() {
		wireMockServer.stop();
		mockClient.close();
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
		builder.addBootstrapRegistryInitializer(new Fabric8ConfigServerBootstrapper());
		return builder;
	}

	private String[] addDefaultEnv(String[] env) {
		Set<String> set = new LinkedHashSet<>();
		if (env != null && env.length > 0) {
			set.addAll(Arrays.asList(env));
		}
		set.add("spring.cloud.config.discovery.enabled=true");
		set.add("spring.config.import=optional:configserver:");
		set.add("spring.cloud.config.discovery.service-id=spring-cloud-kubernetes-configserver");
		set.add("spring.cloud.kubernetes.client.namespace=test");
		set.add("server.port=0");
		return set.toArray(new String[0]);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
