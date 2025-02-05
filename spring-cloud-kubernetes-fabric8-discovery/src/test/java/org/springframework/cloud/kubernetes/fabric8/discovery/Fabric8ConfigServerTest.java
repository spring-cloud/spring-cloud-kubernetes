/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Test that proves that this
 * <a href="https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1831">issue</a>
 * is fixed.
 *
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:, optional:configserver:", "dummy.config.loader.enabled=true" })
class Fabric8ConfigServerTest {

	private static WireMockServer wireMockServer;

	@Autowired
	private ApplicationContext applicationContext;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().port(8888));
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());
	}

	@AfterAll
	static void after() {
		WireMock.shutdownServer();
		wireMockServer.stop();
	}

	@Test
	void test() {
		stubFor(get(urlEqualTo("/application/default")).willReturn(aResponse().withStatus(200).withBody("{}")));
		Assertions.assertThat(applicationContext).isNotNull();
	}

	@SpringBootApplication
	protected static class TestConfig {

	}

}
