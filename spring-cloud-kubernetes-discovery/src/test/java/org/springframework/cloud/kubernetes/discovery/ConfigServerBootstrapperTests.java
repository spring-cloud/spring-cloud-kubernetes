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

package org.springframework.cloud.kubernetes.discovery;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
class ConfigServerBootstrapperTests {

	private static WireMockServer wireMockServer;

	protected ConfigurableApplicationContext context;

	@AfterEach
	void close() {
		wireMockServer.stop();
		if (this.context != null) {
			this.context.close();
		}
	}

	@BeforeEach
	void beforeAll() throws JsonProcessingException {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		String APPS_NAME = """
								[{
									"instanceId": "uid2",
									"serviceId": "spring-cloud-kubernetes-configserver",
									"host": "localhost",
									"port": "%s",
									"uri": "%s",
									"secure":false,
									"metadata":{"spring": "true", "http": "8080", "k8s": "true"},
									"namespace": "namespace1",
									"cluster": null,
									"scheme":"http"
					}]
				""".formatted(wireMockServer.port(), wireMockServer.baseUrl());

		stubFor(get("/apps/spring-cloud-kubernetes-configserver").willReturn(
				aResponse().withStatus(200).withBody(APPS_NAME).withHeader("content-type", "application/json")));
		Environment environment = new Environment("test", "default");
		Map<String, Object> properties = new HashMap<>();
		properties.put("hello", "world");
		PropertySource p = new PropertySource("p1", properties);
		environment.add(p);
		ObjectMapper objectMapper = new ObjectMapper();
		stubFor(get("/application/default")
				.willReturn(aResponse().withStatus(200).withBody(objectMapper.writeValueAsString(environment))
						.withHeader("content-type", "application/json")));
	}

	@Test
	void testBootstrapper() {
		this.context = setup().run();
		verify(1, getRequestedFor(urlEqualTo("/apps/spring-cloud-kubernetes-configserver")));
		assertThat(this.context.getEnvironment().getProperty("hello")).isEqualTo("world");
	}

	SpringApplicationBuilder setup(String... env) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(TestConfig.class)
				.properties(addDefaultEnv(env));
		builder.addBootstrapRegistryInitializer(new ConfigServerBootstrapper());
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
		set.add("spring.cloud.kubernetes.discovery.discoveryServerUrl=" + wireMockServer.baseUrl());
		return set.toArray(new String[0]);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
