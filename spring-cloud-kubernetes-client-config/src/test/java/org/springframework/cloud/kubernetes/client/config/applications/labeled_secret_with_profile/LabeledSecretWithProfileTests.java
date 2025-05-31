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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.Blue;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.Green;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.GreenK8s;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.GreenProd;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.GreenPurple;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_secret_with_profile.properties.GreenPurpleK8s;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@link org.springframework.cloud.kubernetes.client.config.bootstrap.stubs.LabeledSecretWithProfileConfigurationStub}
 *
 * @author wind57
 */
abstract class LabeledSecretWithProfileTests {

	@Autowired
	private Blue blue;

	@Autowired
	private Green green;

	@Autowired
	private GreenK8s greenK8s;

	@Autowired
	private GreenProd greenProd;

	@Autowired
	private GreenPurple greenPurple;

	@Autowired
	private GreenPurpleK8s greenPurpleK8s;

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
	 *     this one is taken from : "blue.one". We find "color-secret" by labels.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		assertThat(blue.getOne()).isEqualTo("1");
	}

	@Test
	void testGreen() {
		assertThat(green.getTwo()).isEqualTo("2");
	}

	@Test
	void testGreenK8s() {
		assertThat(greenK8s.getSix()).isEqualTo("6");
	}

	@Test
	void testGreenProd() {
		assertThat(greenProd.getSeven()).isEqualTo("7");
	}

	@Test
	void testGreenPurple() {
		assertThat(greenPurple.getEight()).isEqualTo("8");
	}

	@Test
	void testGreenPurpleK8s() {
		assertThat(greenPurpleK8s.getEight()).isEqualTo("eight-ish");
	}

}
