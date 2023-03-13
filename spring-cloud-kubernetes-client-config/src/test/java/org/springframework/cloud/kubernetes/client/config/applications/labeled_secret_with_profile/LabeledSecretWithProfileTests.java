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

package org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/*
 * <pre>
 *   - secret with name "color-secret", with labels: "{color: blue}" and "explicitPrefix: blue"
 *   - secret with name "green-secret", with labels: "{color: green}" and "explicitPrefix: blue-again"
 *   - secret with name "red-secret", with labels "{color: not-red}" and "useNameAsPrefix: true"
 *   - secret with name "yellow-secret" with labels "{color: not-yellow}" and useNameAsPrefix: true
 *   - secret with name "color-secret-k8s", with labels : "{color: not-blue}"
 *   - secret with name "green-secret-k8s", with labels : "{color: green-k8s}"
 *   - secret with name "green-secret-prod", with labels : "{color: green-prod}"
 * </pre>
 */

/**
 * Stubs for this test are in
 * {@link org.springframework.cloud.kubernetes.client.config.boostrap.stubs.LabeledSecretWithProfileConfigurationStub}
 *
 * @author wind57
 */
abstract class LabeledSecretWithProfileTests {

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
	 *     this one is taken from : "blue.one". We find "color-secret" by labels, and
	 *     "color-secrets-k8s" exists, but "includeProfileSpecificSources=false", thus not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		this.webClient.get().uri("/labeled-secret/profile/blue").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("1"));
	}

	/**
	 * <pre>
	 *   this one is taken from : "green-purple-secret.green-purple-secret-k8s.green-secret.green-secret-k8s.green-secret-prod".
	 *   We find "green-secret" by labels, also "green-secrets-k8s" and "green-secrets-prod" exists,
	 *   because "includeProfileSpecificSources=true" is set. Also "green-purple-secret" and "green-purple-secret-k8s"
	 * 	 are found.
	 * </pre>
	 */
	@Test
	void testGreen() {
		this.webClient.get().uri("/labeled-secret/profile/green").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("2#6#7#eight-ish"));
	}

}
