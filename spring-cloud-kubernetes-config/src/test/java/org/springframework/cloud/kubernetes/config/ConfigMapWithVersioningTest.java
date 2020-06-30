/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Piotr Minkowski
 */
public class ConfigMapWithVersioningTest {

	private static final String APP_NAME = "versioning-test";

	private ConfigurableApplicationContext context;

	static {
		System.setProperty("info.app.version", "1.0");
		System.setProperty("spring.application.name", APP_NAME);
		System.setProperty("spring.cloud.kubernetes.config.enableVersioning", "true");
	}

	@Rule
	public KubernetesServer server = new KubernetesServer(true, true);

	@Test
	public void testVersioning() {
		String namespace = "app-props";
		ConfigMap map = new ConfigMapBuilder().withNewMetadata()
				.withName("versioning-test-1").withNamespace(namespace)
				.addToLabels("app", APP_NAME).addToLabels("version", "1.0").endMetadata()
				.addToData("KEY", "123").build();
		server.getClient().configMaps().inNamespace(namespace).create(map);
		ConfigMapPropertySource source = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), APP_NAME, namespace,
				new String[] {}, true, new HashMap<>());
		assertThat(source.getProperty("KEY")).isEqualTo("123");
	}

}
