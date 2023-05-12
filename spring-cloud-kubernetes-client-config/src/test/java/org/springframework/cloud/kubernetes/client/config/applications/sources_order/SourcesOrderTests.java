/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.applications.sources_order;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The stub data for this test is in :
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.SourcesOrderConfigurationStub}
 *
 * @author wind57
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SourcesOrderApp.class,
		properties = { "spring.cloud.bootstrap.name=sources-order", "sources.order.stub=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true" })
@AutoConfigureWebTestClient
abstract class SourcesOrderTests {

	@Autowired
	private WebTestClient webClient;

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@AfterAll
	static void afterAll() {
		WireMock.shutdownServer();
	}

	/**
	 * <pre>
	 *	 1. There is one secret deployed: my-secret. It has two properties: {my.one=one, my.key=from-secret}
	 *	 2. There is one configmap deployed: my-configmap. It has two properties: {my.two=two, my.key=from-configmap}
	 *
	 *	 We invoke three endpoints: /one, /two, /key.
	 *	 The first two prove that both the secret and configmap have been read, the last one proves that
	 *	 config maps have a higher precedence.
	 * </pre>
	 */
	@Test
	void test() {
		this.webClient.get().uri("/one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("one"));
		this.webClient.get().uri("/two").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("two"));

		this.webClient.get().uri("/key").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("from-configmap"));
	}

}
