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

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.config.ConfigMapTestUtil.readResourceFile;

/**
 * @author Charles Moulliard
 */
public class ConfigMapsTest {

	@Rule
	public KubernetesServer server = new KubernetesServer();

	@Test
	public void testConfigMapList() {
		this.server.expect().withPath("/api/v1/namespaces/ns1/configmaps")
				.andReturn(200, new ConfigMapListBuilder().build()).once();

		KubernetesClient client = this.server.getClient();

		ConfigMapList configMapList = client.configMaps().inNamespace("ns1").list();
		assertThat(configMapList).isNotNull();
		assertThat(configMapList.getItems().size()).isEqualTo(0);
	}

	@Test
	public void testConfigMapGet() {
		this.server.expect().withPath("/api/v1/namespaces/ns2/configmaps")
				.andReturn(200,
						new ConfigMapBuilder().withNewMetadata()
								.withName("reload-example").endMetadata()
								.addToData("KEY", "123").build())
				.once();

		KubernetesClient client = this.server.getClient();
		ConfigMapList configMapList = client.configMaps().inNamespace("ns2").list();
		assertThat(configMapList).isNotNull();
		assertThat(configMapList.getAdditionalProperties()).containsKey("data");
		@SuppressWarnings("unchecked")
		Map<String, String> data = (Map<String, String>) configMapList
				.getAdditionalProperties().get("data");
		assertThat(data.get("KEY")).isEqualTo("123");
	}

	@Test
	public void testConfigMapFromSingleApplicationProperties() {
		String configMapName = "app-properties-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200, new ConfigMapBuilder().withNewMetadata()
						.withName(configMapName).endMetadata()
						.addToData("application.properties",
								readResourceFile("application.properties"))
						.build())
				.once();

		ConfigMapPropertySource cmps = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), configMapName);

		assertThat(cmps.getProperty("dummy.property.string1")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int1")).isEqualTo("1");
		assertThat(cmps.getProperty("dummy.property.bool1")).isEqualTo("true");
	}

	@Test
	public void testConfigMapFromSingleApplicationYaml() {
		String configMapName = "app-yaml-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200,
						new ConfigMapBuilder().withNewMetadata().withName(configMapName)
								.endMetadata()
								.addToData("application.yaml",
										readResourceFile("application.yaml"))
								.build())
				.once();

		ConfigMapPropertySource cmps = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), configMapName);

		assertThat(cmps.getProperty("dummy.property.string2")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int2")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool2")).isEqualTo(true);
	}

	@Test
	public void testConfigMapFromSingleNonStandardFileName() {
		String configMapName = "single-non-standard-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200, new ConfigMapBuilder().withNewMetadata()
						.withName(configMapName).endMetadata()
						.addToData("adhoc.yml", readResourceFile("adhoc.yml")).build())
				.once();

		ConfigMapPropertySource cmps = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), configMapName);

		assertThat(cmps.getProperty("dummy.property.string3")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int3")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool3")).isEqualTo(true);
	}

	@Test
	public void testConfigMapFromSingleInvalidPropertiesContent() {
		String configMapName = "single-unparseable-properties-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200,
						new ConfigMapBuilder().withNewMetadata().withName(configMapName)
								.endMetadata()
								.addToData("application.properties", "somevalue").build())
				.once();

		new ConfigMapPropertySource(this.server.getClient().inNamespace(namespace),
				configMapName);

		// no exception is thrown for unparseable content
	}

	@Test
	public void testConfigMapFromSingleInvalidYamlContent() {
		String configMapName = "single-unparseable-yaml-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200,
						new ConfigMapBuilder().withNewMetadata().withName(configMapName)
								.endMetadata().addToData("application.yaml", "somevalue")
								.build())
				.once();

		new ConfigMapPropertySource(this.server.getClient().inNamespace(namespace),
				configMapName);

		// no exception is thrown for unparseable content
	}

	@Test
	public void testConfigMapFromMultipleApplicationProperties() {
		String configMapName = "app-multiple-properties-test";
		String namespace = "app-props";
		this.server.expect()
				.withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace,
						configMapName))
				.andReturn(200,
						new ConfigMapBuilder().withNewMetadata().withName(configMapName)
								.endMetadata()
								.addToData("application.properties",
										readResourceFile("application.properties"))
								.addToData("adhoc.properties",
										readResourceFile("adhoc.properties"))
								.build())
				.once();

		ConfigMapPropertySource cmps = new ConfigMapPropertySource(
				this.server.getClient().inNamespace(namespace), configMapName);

		// application.properties should be read correctly
		assertThat(cmps.getProperty("dummy.property.string1")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int1")).isEqualTo("1");
		assertThat(cmps.getProperty("dummy.property.bool1")).isEqualTo("true");

		// the adhoc.properties file should not be parsed
		assertThat(cmps.getProperty("dummy.property.bool2")).isNull();
		assertThat(cmps.getProperty("dummy.property.bool2")).isNull();
		assertThat(cmps.getProperty("dummy.property.bool2")).isNull();
	}

}
