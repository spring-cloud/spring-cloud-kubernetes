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

package org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Stud data is in
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.LabeledConfigMapWithProfileConfigurationStub}
 *
 * @author wind57
 */
abstract class LabeledConfigMapWithProfileTests {

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
	 *     this one is taken from : "blue.one". We find "color-configmap" by labels, and
	 *     "color-configmap-k8s" exists, but "includeProfileSpecificSources=false", thus not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		this.webClient.get().uri("/labeled-configmap/profile/blue").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("1"));
	}

	/**
	 * <pre>
	 *   this one is taken from : ""green-configmap.green-configmap-k8s.green-configmap-prod.green-purple-configmap.green-purple-configmap-k8s"".
	 *   We find "green-configmap" by labels, also "green-configmap-k8s", "green-configmap-prod" exists,
	 *   because "includeProfileSpecificSources=true" is set. Also "green-purple-configmap" and "green-purple-configmap-k8s"
	 *   are found.
	 * </pre>
	 */
	@Test
	void testGreen() {
		this.webClient.get().uri("/labeled-configmap/profile/green").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("2#6#7#eight-ish"));
	}

}
