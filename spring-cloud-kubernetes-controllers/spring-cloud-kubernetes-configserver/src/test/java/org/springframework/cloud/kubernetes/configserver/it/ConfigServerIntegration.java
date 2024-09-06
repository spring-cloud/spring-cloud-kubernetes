/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
abstract class ConfigServerIntegration {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	WireMockServer wireMockServer;

	@BeforeEach
	void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList().addItemsItem(new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName("test-cm").withNamespace("default").withResourceVersion("1").build())
			.addToData("test-cm-dev.yaml", "dummy:\n  property:\n    string2: \"dev\"\n    int2: 1\n    bool2: false\n")
			.addToData("test-cm-qa.yaml", "dummy:\n  property:\n    string2: \"qa\"\n    int2: 2\n    bool2: true\n")
			.addToData("test-cm-prod.yaml",
					"dummy:\n  property:\n    string2: \"prod\"\n    int2: 3\n    bool2: true\n")
			.addToData("test-cm.yaml", "dummy:\n  property:\n    string2: \"default\"\n    int2: 4\n    bool2: true\n")
			.addToData("app.name", "test")
			.build());

		V1SecretList TEST_SECRET = new V1SecretListBuilder()
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("1").build())
			.addToItems(new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("test-cm")
					.withResourceVersion("0")
					.withNamespace("default")
					.build())
				.addToData("password", "p455w0rd".getBytes())
				.addToData("username", "user".getBytes())
				.build())
			.build();

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_SECRET))));
	}

	@Test
	void enabled() {
		Environment env = testRestTemplate.getForObject("/test-cm/default", Environment.class);
		assertThat(env.getPropertySources().size()).isEqualTo(2);
		assertThat(env.getPropertySources().get(0).getName().equals("configmap.test-cm.default.default")).isTrue();
		assertThat(env.getPropertySources().get(0).getSource().get("app.name")).isEqualTo("test");
		assertThat(env.getPropertySources().get(1).getName().equals("secret.test-cm.default.default")).isTrue();
		assertThat(env.getPropertySources().get(1).getSource().get("password")).isEqualTo("p455w0rd");
		assertThat(env.getPropertySources().get(1).getSource().get("username")).isEqualTo("user");

		Environment devprod = testRestTemplate.getForObject("/test-cm/dev,prod", Environment.class);
		assertThat(devprod.getPropertySources().size()).isEqualTo(4);
		assertThat(devprod.getPropertySources().get(0).getName().equals("configmap.test-cm.default.prod")).isTrue();
		assertThat(devprod.getPropertySources().get(0).getSource().size()).isEqualTo(3);
		assertThat(devprod.getPropertySources().get(0).getSource().get("dummy.property.int2")).isEqualTo(3);
		assertThat(devprod.getPropertySources().get(0).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(devprod.getPropertySources().get(0).getSource().get("dummy.property.string2")).isEqualTo("prod");
		assertThat(devprod.getPropertySources().get(1).getName().equals("configmap.test-cm.default.dev")).isTrue();
		assertThat(devprod.getPropertySources().get(1).getSource().size()).isEqualTo(3);
		assertThat(devprod.getPropertySources().get(1).getSource().get("dummy.property.int2")).isEqualTo(1);
		assertThat(devprod.getPropertySources().get(1).getSource().get("dummy.property.bool2")).isEqualTo(false);
		assertThat(devprod.getPropertySources().get(1).getSource().get("dummy.property.string2")).isEqualTo("dev");
		assertThat(devprod.getPropertySources().get(2).getName().equals("configmap.test-cm.default.default")).isTrue();
		assertThat(devprod.getPropertySources().get(2).getSource().size()).isEqualTo(4);
		assertThat(devprod.getPropertySources().get(2).getSource().get("app.name")).isEqualTo("test");
		assertThat(devprod.getPropertySources().get(2).getSource().get("dummy.property.int2")).isEqualTo(4);
		assertThat(devprod.getPropertySources().get(2).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(devprod.getPropertySources().get(2).getSource().get("dummy.property.string2")).isEqualTo("default");
		assertThat(devprod.getPropertySources().get(3).getName().equals("secret.test-cm.default.default")).isTrue();
		assertThat(devprod.getPropertySources().get(3).getSource().size()).isEqualTo(2);
		assertThat(devprod.getPropertySources().get(3).getSource().get("password")).isEqualTo("p455w0rd");
		assertThat(devprod.getPropertySources().get(3).getSource().get("username")).isEqualTo("user");
	}

}
