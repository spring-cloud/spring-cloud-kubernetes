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

package org.springframework.cloud.kubernetes.configserver.configurations;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author Ryan Baxter
 */
@Configuration
@ConditionalOnProperty(value = "test.first.config.enabled", havingValue = "true", matchIfMissing = false)
public class FirstConfig {

	@Bean
	WireMockServer wireMockServer() {
		WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		return wireMockServer;
	}

	@Bean
	ApiClient apiClient(WireMockServer wireMockServer) {
		return new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
	}

	@Bean
	CoreV1Api coreApi(ApiClient apiClient) {
		return new CoreV1Api(apiClient);
	}

}
