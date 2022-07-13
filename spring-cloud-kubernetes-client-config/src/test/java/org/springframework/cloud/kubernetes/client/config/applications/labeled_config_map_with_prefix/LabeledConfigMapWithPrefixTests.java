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

package org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_prefix;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Stud data is in
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.LabeledConfigMapWithPrefixConfigurationStub}
 *
 * @author wind57
 */
abstract class LabeledConfigMapWithPrefixTests {

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
	 *   'spring.cloud.kubernetes.configmap.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.configmap.sources[0].useNameAsPrefix=false'
	 * 	 ("one.property", "one")
	 *
	 * 	 As such: @ConfigurationProperties("one")
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/labeled-configmap/prefix/one").exchange().expectStatus().isOk()
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
		this.webClient.get().uri("/labeled-configmap/prefix/two").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[2].labels=letter:c'
	 * 	 ("property", "three")
	 *
	 *   We find the configmap by labels, and use it's name as the prefix.
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "configmap-three")
	 * </pre>
	 */
	@Test
	void testThree() {
		this.webClient.get().uri("/labeled-configmap/prefix/three").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("three"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[3].labels=letter:d'
	 * 	 ("property", "four")
	 *
	 *   We find the configmap by labels, and use it's name as the prefix.
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "configmap-four")
	 * </pre>
	 */
	@Test
	void testFour() {
		this.webClient.get().uri("/labeled-configmap/prefix/four").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("four"));
	}

}
