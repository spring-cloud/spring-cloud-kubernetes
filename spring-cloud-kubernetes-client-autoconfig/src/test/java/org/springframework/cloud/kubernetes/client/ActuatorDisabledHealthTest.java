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

package org.springframework.cloud.kubernetes.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.example.App;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "management.health.kubernetes.enabled=false", "management.endpoint.health.show-details=always",
				"management.endpoint.health.show-components=always", "spring.main.cloud-platform=KUBERNETES",
				"management.endpoints.web.exposure.include=health" })
class ActuatorDisabledHealthTest {

	@Autowired
	private ReactiveHealthContributorRegistry registry;

	@Autowired
	private WebTestClient webClient;

	@Value("${local.server.port}")
	private int port;

	@Test
	void healthEndpointShouldNotContainKubernetes() {
		String response = this.webClient.get().uri("http://localhost:{port}/actuator/health", this.port)
				.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(String.class)
				.returnResult().getResponseBody();

		JsonObject obj = new Gson().fromJson(response, JsonObject.class);

		// kubernetes is not part of the components
		Assertions.assertNull(obj.getAsJsonObject("components").get("kubernetes"));

		Assertions.assertNull(registry.getContributor("kubernetes"),
				"reactive kubernetes contributor must NOT be present when 'management.health.kubernetes.enabled=false'");
	}

}
