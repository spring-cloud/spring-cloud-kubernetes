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

package org.springframework.cloud.kubernetes.fabric8.config.array_with_profiles;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.config.import=kubernetes:" })
@EnableKubernetesMockClient(crud = true, https = false)
@AutoConfigureWebTestClient
class ConfigDataMultipleYamlDocumentsNoActiveProfileTests extends ArrayWithProfiles {

	@Autowired
	private WebTestClient webClient;

	private static KubernetesClient mockClient;

	@BeforeAll
	public static void setUpBeforeClass() {
		setUpBeforeClass(mockClient);
	}

	/**
	 * <pre>
	 *     - dev is not an active profile
	 *     - which means beans.items = [Item 10]
	 * </pre>
	 */
	@Test
	void testItemsEndpoint() {
		this.webClient.get()
			.uri("/api/items")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(List.class)
			.isEqualTo(List.of("Item 10"));
	}

	/**
	 * <pre>
	 *     - dev is not an active profile
	 *     - which means beans.map = {"name", "ER", "role", "user"}
	 * </pre>
	 */
	@Test
	void testMapEndpoint() {
		this.webClient.get()
			.uri("/api/map")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.isEqualTo(Map.of("name", "ER", "role", "user"));
	}

}
