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

package org.springframework.cloud.kubernetes.client.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.applications.include_profile_specific_sources.IncludeProfileSpecificSourcesApp;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.cloud.kubernetes.client.config.boostrap.stubs.IncludeProfileSpecificSourcesConfigurationStub.stubData;

/**
 * @author Ryan Baxter
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = IncludeProfileSpecificSourcesApp.class,
		properties = { "spring.application.name=include-profile-specific-sources",
				"include.profile.specific.sources=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:,classpath:./include-profile-specific-sources.yaml" })
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
public class KubernetesClientConfigMapConfigDataIncludeProfileSpecificSourcesTests
		extends KubernetesClientConfigMapIncludeProfileSpecificSourcesTests {

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	@BeforeAll
	public static void wireMock() {
		WireMockServer server = new WireMockServer(options().dynamicPort());
		server.start();
		WireMock.configureFor("localhost", server.port());
		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::kubernetesApiClient)
				.thenReturn(new ClientBuilder().setBasePath(server.baseUrl()).build());
		stubData();
	}

	@AfterAll
	static void teardown() {
		clientUtilsMock.close();
	}

}
