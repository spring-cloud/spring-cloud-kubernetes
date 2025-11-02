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

package org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.Blue;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.Green;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.GreenK8s;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.GreenProd;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.GreenPurple;
import org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile.properties.GreenPurpleK8s;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stub data is in
 * {@link org.springframework.cloud.kubernetes.client.config.bootstrap.stubs.LabeledConfigMapWithProfileConfigurationStub}
 *
 * @author wind57
 */
@AutoConfigureWebTestClient
abstract class LabeledConfigMapWithProfileTests {

	@Autowired
	private WebTestClient webClient;

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
		assertThat(blue.getOne()).isEqualTo("1");
	}

	// found by labels
	@Test
	void testGreen() {
		assertThat(green.getTwo()).isEqualTo("2");
	}

	// found because above is found, plus active profile is included
	@Test
	void testGreenK8s() {
		assertThat(greenK8s.getSix()).isEqualTo("6");
	}

	// found because above is found, plus active profile is included
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
