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

package org.springframework.cloud.kubernetes.fabric8.config.single_source_multiple_files;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 *
 * issue: https://github.com/spring-cloud/spring-cloud-kubernetes/issues/640
 */
abstract class SingleSourceMultipleFilesTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		SingleSourceMultipleFilesTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = new HashMap<>();
		one.put("fruit.type", "yummy");
		one.put("fruit.properties", "cool.name=banana");
		one.put("fruit-color.properties", "color.when.raw=green\ncolor.when.ripe=yellow");

		// this is not taken, since "shape" is not an active profile
		one.put("fruit-shape.properties", "shape.when.raw=small-sphere\nshape.when.ripe=bigger-sphere");
		createConfigmap(one);

	}

	static void createConfigmap(Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s").resource(
				new ConfigMapBuilder().withNewMetadata().withName("my-configmap").endMetadata().addToData(data).build())
				.create();
	}

	/**
	 * <pre>
	 *   "fruit-color.properties" is taken since "spring.application.name=fruit" and
	 *   "color" is an active profile
	 * </pre>
	 */
	@Test
	void color() {
		this.webClient.get().uri("/single_source-multiple-files/color").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("raw:green###ripe:yellow"));
	}

	/**
	 * <pre>
	 *   "fruit.properties" is read, since it matches "spring.application.name"
	 * </pre>
	 */
	@Test
	void name() {
		this.webClient.get().uri("/single_source-multiple-files/name").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("banana"));
	}

	/**
	 * <pre>
	 *   shape profile is not active, thus property "fruit-shape.properties" is skipped
	 *   and as such, a null comes here.
	 * </pre>
	 */
	@Test
	void shape() {
		this.webClient.get().uri("/single_source-multiple-files/shape").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.nullValue());
	}

	/**
	 * <pre>
	 *   this is a non-file property in the configmap
	 * </pre>
	 */
	@Test
	void type() {
		this.webClient.get().uri("/single_source-multiple-files/type").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("yummy"));
	}

}
