/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.leader.election;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionUtil.HOLDER_IDENTITY;
import static org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionUtil.wireMockServer;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=info",
				"management.endpoint.info.show-details=always", "spring.cloud.kubernetes.leader.election.enabled=true",
				"spring.main.allow-bean-definition-overriding=true",
				"spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false" },
		classes = { KubernetesClientLeaderElectionTestApp.class,
				KubernetesClientLeaderElectionUtil.ApiClientConfiguration.class })
@AutoConfigureWebTestClient
class KubernetesClientLeaderElectionInfoContributorIsLeaderTest {

	@LocalManagementPort
	private int port;

	@Autowired
	private WebTestClient webClient;

	private static MockedStatic<LeaderUtils> leaderUtilsMockedStatic;

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void beforeAll() {
		leaderUtilsMockedStatic = Mockito.mockStatic(LeaderUtils.class);
		leaderUtilsMockedStatic.when(LeaderUtils::hostName).thenReturn(HOLDER_IDENTITY);
		wireMockServer = wireMockServer();
	}

	@AfterAll
	static void afterAll() {
		leaderUtilsMockedStatic.close();
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@Test
	void infoEndpointIsLeaderTest() {
		webClient.get()
			.uri("http://localhost:{port}/actuator/info", port)
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("leaderElection.isLeader")
			.isEqualTo(true)
			.jsonPath("leaderElection.leaderId")
			.isEqualTo(HOLDER_IDENTITY);
	}

}
