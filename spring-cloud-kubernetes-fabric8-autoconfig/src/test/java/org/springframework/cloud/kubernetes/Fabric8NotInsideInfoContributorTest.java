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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=info",
				"management.endpoint.info.show-details=always", "management.info.kubernetes.enabled=true" })
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8NotInsideInfoContributorTest {

	private static KubernetesClient client;

	@Autowired
	private WebTestClient webClient;

	@LocalManagementPort
	private int port;

	/**
	 * <pre>
	 *    "kubernetes":{
	 *        "inside":false
	 *    }
	 * </pre>
	 */
	@Test
	void infoEndpointShouldContainKubernetes() {
		this.webClient.get().uri("http://localhost:{port}/actuator/info", this.port).accept(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isOk().expectBody().jsonPath("kubernetes.inside").isEqualTo("false");
	}

}
