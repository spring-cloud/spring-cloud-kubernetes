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

package org.springframework.cloud.kubernetes.configserver.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.kubernetes.client.openapi.JSON.serialize;

/**
 * @author wind57
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(properties = { "spring.cloud.kubernetes.secrets.enabled=true",
	"spring.cloud.kubernetes.config.enabled=true", "spring.main.cloud-platform=KUBERNETES",
	"spring.cloud.kubernetes.client.namespace=default" })
class DuplicatedPropertySourcesTest {

	@Autowired
	private TestRestTemplate testRestTemplate;

	private static final V1SecretList SECRETS = new V1SecretListBuilder().build();

	// fruit = apple
	// color = generic
	private static final V1ConfigMap MY_CONFIGMAP = new V1ConfigMapBuilder()
		.addToData("fruit", "apple").addToData("color", "generic").build();

	// shape=round
	private static final V1ConfigMap MY_CONFIGMAP_SHAPE = new V1ConfigMapBuilder()
		.addToData("shape", "round").build();

	// color=green
	private static final V1ConfigMap MY_CONFIGMAP_COLOR = new V1ConfigMapBuilder()
		.addToData("color", "green").build();

	private static final V1ConfigMapList CONFIGMAPS = new V1ConfigMapList().addItemsItem(MY_CONFIGMAP)
		.addItemsItem(MY_CONFIGMAP_SHAPE).addItemsItem(MY_CONFIGMAP_COLOR);

	@BeforeAll
	static void beforeAll() {
		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(CONFIGMAPS))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
			.willReturn(aResponse().withStatus(200).withBody(serialize(SECRETS))));
	}

//	@AfterEach
//	void afterEach() {
//		WireMock.reset();
//		WireMock.shutdownServer();
//		wireMockServer.stop();
//		wireMockServer.shutdownServer();
//	}

}
