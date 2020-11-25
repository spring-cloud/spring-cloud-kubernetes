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

package org.springframework.cloud.kubernetes.leader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		// Make sure test passes without Kubernetes cluster
		"spring.cloud.kubernetes.leader.autoStartup=false" })
public class LeaderAutoConfigurationTests {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private WebTestClient webClient;

	@Test
	public void contextLoads() {
	}

	@Test
	public void infoEndpointShouldContainLeaderElection() {
		this.webClient.get().uri("http://localhost:{port}/actuator/info", this.port).accept(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isOk().expectBody(String.class).value(containsString("kubernetes"));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfig {

	}

}
