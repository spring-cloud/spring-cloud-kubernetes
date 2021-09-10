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

package org.springframework.cloud.kubernetes.client;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.example.App;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Robert McNees
 *
 * This tests that the apiClient created in KubernetesClientAutoConfiguration will not set
 * itself as the default apiClient. This is to avoid overwriting the user's
 * defaultApiClient if they include this project.
 */
@SpringBootTest(classes = App.class, properties = { "kubernetes.informer.enabled=false" })
// kubernetes.informer is disabled because
// io.kubernetes...KubernetesInformerAutoConfiguration
// creates a defaultApiClient that will be autowired instead of the
// ApiClient created in KubernetesClientAutoConfiguration
public class KubernetesClientDefaultApiClientTests {

	@Autowired
	private ApiClient apiClient;

	@Test
	public void testCreatedApiClientIsNotDefault() {
		assertThat(apiClient).isNotNull();

		ApiClient defaultApiClient = io.kubernetes.client.openapi.Configuration.getDefaultApiClient();
		assertThat(defaultApiClient).isNotNull();

		assertThat(defaultApiClient).isNotSameAs(apiClient);
	}

}
