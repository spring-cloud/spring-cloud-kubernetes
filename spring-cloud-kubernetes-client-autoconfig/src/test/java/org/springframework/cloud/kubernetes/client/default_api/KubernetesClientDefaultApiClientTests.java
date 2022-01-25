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

package org.springframework.cloud.kubernetes.client.default_api;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import io.kubernetes.client.openapi.ApiClient;
import okhttp3.Request;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Robert McNees
 *
 * This tests that the apiClient created in KubernetesClientAutoConfiguration will not set
 * itself as the default apiClient. This is to avoid overwriting the user's
 * defaultApiClient if they include this project.
 *
 * kubernetes informer is disabled because KubernetesInformerAutoConfiguration creates a
 * defaultApiClient that will be autowired instead of the ApiClient created in
 * KubernetesClientAutoConfiguration
 */

class KubernetesClientDefaultApiClientTests {

	private static final String DISABLE_INFORMER = "kubernetes.informer.enabled=false";

	private static final String USER_AGENT = "spring.cloud.kubernetes.client.userAgent=non-default";

	private static final String ENABLED_K8S = "spring.main.cloud-platform=KUBERNETES";

	@SpringBootTest(classes = KubernetesClientDefaultApiClientTests.App.class,
			properties = { DISABLE_INFORMER, ENABLED_K8S })
	@Nested
	class DefaultApiClientNotSameAsApiClient {

		@Autowired
		private ApiClient apiClient;

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void testCreatedApiClientIsNotDefault() {
			assertThat(apiClient).isNotNull();

			ApiClient defaultApiClient = io.kubernetes.client.openapi.Configuration.getDefaultApiClient();
			assertThat(defaultApiClient).isNotNull();

			assertThat(defaultApiClient).isNotSameAs(apiClient);
		}

	}

	@SpringBootTest(classes = KubernetesClientDefaultApiClientTests.App.class,
			properties = { DISABLE_INFORMER, ENABLED_K8S })
	@Nested
	class ApiClientUserAgentDefaultHeader {

		@Autowired
		private ApiClient apiClient;

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void testApiClientUserAgentDefaultHeader() throws MalformedURLException {
			assertThat(apiClient).isNotNull();
			Request.Builder builder = new Request.Builder();
			apiClient.processHeaderParams(Collections.emptyMap(), builder);
			assertThat(builder.url(new URL("http://example.com")).build().headers().get("User-Agent"))
					.isEqualTo("Spring-Cloud-Kubernetes-Application");
		}

	}

	@SpringBootTest(classes = KubernetesClientDefaultApiClientTests.App.class,
			properties = { DISABLE_INFORMER, USER_AGENT, ENABLED_K8S })
	@Nested
	class ApiClientUserAgentNonDefaultHeader {

		@Autowired
		private ApiClient apiClient;

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void testApiClientUserAgentDefaultHeader() throws MalformedURLException {
			assertThat(apiClient).isNotNull();
			Request.Builder builder = new Request.Builder();
			apiClient.processHeaderParams(Collections.emptyMap(), builder);
			assertThat(builder.url(new URL("http://example.com")).build().headers().get("User-Agent"))
					.isEqualTo("non-default");
		}

	}

	@SpringBootApplication
	static class App {

	}

}
