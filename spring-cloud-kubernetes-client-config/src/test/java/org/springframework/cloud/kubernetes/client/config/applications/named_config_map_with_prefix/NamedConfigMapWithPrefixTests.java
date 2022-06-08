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

package org.springframework.cloud.kubernetes.client.config.applications.named_config_map_with_prefix;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The stub data for this test is in :
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.NamedConfigMapWithPrefixConfigurationStub}
 *
 * @author wind57
 */
abstract class NamedConfigMapWithPrefixTests {

	@Autowired
	private WebTestClient webClient;

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	@AfterAll
	static void afterAll() {
		WireMock.shutdownServer();
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[0].useNameAsPrefix=false'
	 * 	 ("one.property", "one")
	 *
	 * 	 As such: @ConfigurationProperties("one")
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/named-configmap/prefix/one").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("one"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[1].explicitPrefix=two'
	 * 	 ("property", "two")
	 *
	 * 	 As such: @ConfigurationProperties("two")
	 * </pre>
	 */
	@Test
	void testTwo() {
		this.webClient.get().uri("/named-configmap/prefix/two").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[2].name=config-map-three'
	 * 	 ("property", "three")
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "config-map-three")
	 * </pre>
	 */
	@Test
	void testThree() {
		this.webClient.get().uri("/named-configmap/prefix/three").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("three"));
	}

}
