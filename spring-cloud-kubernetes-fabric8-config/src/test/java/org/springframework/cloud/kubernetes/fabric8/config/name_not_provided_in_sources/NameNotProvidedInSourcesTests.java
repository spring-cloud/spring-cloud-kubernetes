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

package org.springframework.cloud.kubernetes.fabric8.config.name_not_provided_in_sources;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NameNotProvidedInSourcesApp.class,
		properties = { "spring.cloud.bootstrap.name=config-map-name-not-provided-in-sources",
				"spring.main.cloud-platform=KUBERNETES" })
@EnableKubernetesMockClient(crud = true, https = false)
class NameNotProvidedInSourcesTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	@BeforeAll
	static void setUpBeforeClass() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = Collections.singletonMap("configmap.property", "one");

		// we do not provide any name for an explicit config map, but we do "default" to
		// reading
		// one called "application". We also enable "useNameAsPrefix"
		createConfigMap("application", one);

	}

	private static void createConfigMap(String name, Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().addToData(data).withNewMetadata().withName(name).endMetadata().build());
	}

	/*
	 * this test proves that even if we do not set
	 * "spring.cloud.kubernetes.configmap.name", i.e.: we did not specify any explicit
	 * config maps, we will still read one called "application". It also proves that
	 * enabling "useNameAsPrefix" in this case works too.
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("configmap/with-source/one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("one"));
	}

}
