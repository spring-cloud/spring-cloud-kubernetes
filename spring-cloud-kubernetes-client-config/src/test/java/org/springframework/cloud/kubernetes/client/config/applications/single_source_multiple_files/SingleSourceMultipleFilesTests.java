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

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 *
 * Stub for this test is here :
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.SingleSourceMultipleFilesConfigurationStub}
 *
 * issue: https://github.com/spring-cloud/spring-cloud-kubernetes/issues/640
 *
 */
abstract class SingleSourceMultipleFilesTests {

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
	 *   "fruit-color.properties" is taken since "spring.application.name=fruit" and
	 *   "color" is an active profile
	 * </pre>
	 */
	@Test
	void color() {
		this.webClient.get().uri("/single_source-multiple-files/color").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("raw:green###ripe:yellow"));
	}

	/**
	 * <pre>
	 *   "fruit.properties" is read, since it matches "spring.application.name"
	 * </pre>
	 */
	@Test
	void name() {
		this.webClient.get().uri("/single_source-multiple-files/name").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("banana"));
	}

	/**
	 * <pre>
	 *   shape profile is not active, thus property "fruit-shape.properties" is skipped
	 *   and as such, a null comes here.
	 * </pre>
	 */
	@Test
	void shape() {
		this.webClient.get().uri("/single_source-multiple-files/shape").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.nullValue());
	}

	/**
	 * <pre>
	 *   this is a non-file property in the configmap
	 * </pre>
	 */
	@Test
	void type() {
		this.webClient.get().uri("/single_source-multiple-files/type").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("yummy"));
	}

}
