/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Piotr Minkowski
 */
public class ConfigMapWithLabelsTest {

	@Rule
	public KubernetesServer server = new KubernetesServer(true, true);

	@Test
	public void testLabels() {
		final String namespace = "app-props";
		ConfigMap map = new ConfigMapBuilder().withNewMetadata().withName("labels-test")
				.withNamespace(namespace).addToLabels("test", "123").endMetadata()
				.addToData("KEY", "123").build();
		server.getClient().configMaps().inNamespace(namespace).create(map);
		ConfigMapPropertySource source = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), "labels-name", namespace,
				new String[] {}, false, Collections.singletonMap("test", "123"));
		assertThat(source.getProperty("KEY")).isEqualTo("123");
	}

	@Test
	public void testLabelsNotMatched() {
		final String namespace = "app-props";
		ConfigMap map = new ConfigMapBuilder().withNewMetadata()
				.withName("labels-test-not-matched").withNamespace(namespace)
				.addToLabels("testNotMatched", "123").endMetadata()
				.addToData("KEY", "123").build();
		server.getClient().configMaps().inNamespace(namespace).create(map);
		Map<String, String> labels = new HashMap<>();
		labels.put("testNotMatched", "123");
		labels.put("testNotMatched2", "456");
		ConfigMapPropertySource source = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), "labels-name", namespace,
				new String[] {}, false, labels);
		assertThat(source.getProperty("KEY")).isNull();
	}

}
