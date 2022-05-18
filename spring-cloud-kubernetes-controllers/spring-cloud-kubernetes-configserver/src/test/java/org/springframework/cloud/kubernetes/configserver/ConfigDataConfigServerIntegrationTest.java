/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
				"spring.profiles.include=kubernetes", "spring.cloud.kubernetes.secrets.enableApi=true", "debug=true",
				"spring.config.import=kubernetes:" },
		classes = { KubernetesConfigServerApplication.class })
public class ConfigDataConfigServerIntegrationTest extends ConfigServerIntegrationTest {

	private static WireMockServer wireMockServer;

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::kubernetesApiClient)
				.thenReturn(new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build());
	}

	@AfterAll
	static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

}
