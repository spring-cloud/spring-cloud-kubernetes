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

package org.springframework.cloud.kubernetes.configserver.configurations;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@Configuration
@ConditionalOnProperty(value = "test.second.config.enabled", havingValue = "true", matchIfMissing = false)
public class SecondConfig {

	private static V1ConfigMap buildConfigMap() {
		return new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("gateway").withNamespace("default").build())
			.addToData(Constants.APPLICATION_PROPERTIES, "dummy.property.string=gateway")
			.build();
	}

	private static V1Secret buildSecret() {
		return new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("gateway").withNamespace("default").build())
			.addToData("password", "p455w0rd".getBytes())
			.addToData("username", "user".getBytes())
			.build();
	}

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = new V1ConfigMapList().addItemsItem(buildConfigMap());

	private static final V1SecretList SECRET_DEFAULT_LIST = new V1SecretList().addItemsItem(buildSecret());

	@Bean
	WireMockServer wireMockServer() {
		WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(CONFIGMAP_DEFAULT_LIST))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(SECRET_DEFAULT_LIST))));

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
