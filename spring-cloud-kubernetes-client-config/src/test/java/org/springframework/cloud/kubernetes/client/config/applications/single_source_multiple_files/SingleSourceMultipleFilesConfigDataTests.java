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

package org.springframework.cloud.kubernetes.client.config.applications.single_source_multiple_files;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.cloud.kubernetes.client.config.bootstrap.stubs.SingleSourceMultipleFilesConfigurationStub.stubData;

/**
 * @author wind57
 */
@ActiveProfiles("color")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = SingleSourceMultipleFilesApp.class, properties = { "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:,classpath:./single-source-multiple-files.yaml" })
class SingleSourceMultipleFilesConfigDataTests extends SingleSourceMultipleFilesTests {

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	@BeforeAll
	static void wireMock() {
		WireMockServer server = new WireMockServer(options().dynamicPort());
		server.start();
		WireMock.configureFor("localhost", server.port());
		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::kubernetesApiClient)
			.thenReturn(new ClientBuilder().setBasePath(server.baseUrl()).build());
		clientUtilsMock
			.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.any(), Mockito.any(), Mockito.any()))
			.thenReturn("spring-k8s");
		stubData();
	}

	@AfterAll
	static void teardown() {
		clientUtilsMock.close();
	}

}
