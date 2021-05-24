/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "management.endpoints.web.exposure.include=info", "management.endpoint.info.show-details=always",
				"management.info.kubernetes.enabled=true" })
@EnableKubernetesMockClient(crud = true, https = false)
public class Fabric8NotInsideInfoContributorTest {

	private static KubernetesClient client;

	@Autowired
	private WebTestClient webClient;

	@Value("${local.server.port}")
	private int port;

	@Test
	public void infoEndpointShouldContainKubernetes() {
		this.webClient.get().uri("http://localhost:{port}/actuator/info", this.port).accept(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isOk().expectBody(String.class).value(this::validateInfo);
	}

	/**
	 * <pre>
	 *    "kubernetes":{
	 *        "inside":false
	 *    }
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	private void validateInfo(String input) {
		try {
			Map<String, Object> map = new ObjectMapper().readValue(input, new TypeReference<Map<String, Object>>() {

			});
			Map<String, Object> kubernetesProperties = (Map<String, Object>) map.get("kubernetes");
			Assertions.assertFalse((Boolean) kubernetesProperties.get("inside"));
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
