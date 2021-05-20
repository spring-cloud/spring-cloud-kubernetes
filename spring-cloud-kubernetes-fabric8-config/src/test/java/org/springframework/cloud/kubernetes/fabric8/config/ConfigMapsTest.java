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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Charles Moulliard
 */
@EnableKubernetesMockClient(crud = true, https = false)
public class ConfigMapsTest {

	private static KubernetesClient mockClient;

	@Test
	public void testConfigMapList() {
		mockClient.configMaps().inNamespace("ns1").createNew();

		ConfigMapList configMapList = mockClient.configMaps().inNamespace("ns1").list();
		assertThat(configMapList).isNotNull();
		assertThat(configMapList.getItems().size()).isEqualTo(0);
	}

	@Test
	public void testConfigMapGet() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("reload-example").endMetadata()
				.addToData("KEY", "123").build();

		mockClient.configMaps().inNamespace("ns2").create(configMap);

		ConfigMapList configMapList = mockClient.configMaps().inNamespace("ns2").list();
		assertThat(configMapList).isNotNull();
		assertThat(configMapList.getItems().size()).isEqualTo(1);
		assertThat(configMapList.getItems().get(0).getData().size()).isEqualTo(1);
		Map<String, String> resultData = configMapList.getItems().get(0).getData();
		assertThat(resultData.get("KEY")).isEqualTo("123");
	}

	@Test
	public void testConfigMapFromSingleApplicationProperties() {
		String configMapName = "app-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", ConfigMapTestUtil.readResourceFile("application.properties"))
				.build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

		assertThat(cmps.getProperty("dummy.property.string1")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int1")).isEqualTo("1");
		assertThat(cmps.getProperty("dummy.property.bool1")).isEqualTo("true");
	}

	@Test
	public void testConfigMapFromSingleApplicationYaml() {
		String configMapName = "app-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.yaml", ConfigMapTestUtil.readResourceFile("application.yaml")).build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

		assertThat(cmps.getProperty("dummy.property.string2")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int2")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool2")).isEqualTo(true);
	}

	@Test
	public void testConfigMapFromSingleNonStandardFileName() {
		String configMapName = "single-non-standard-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("adhoc.yml", ConfigMapTestUtil.readResourceFile("adhoc.yml")).build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

		assertThat(cmps.getProperty("dummy.property.string3")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int3")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool3")).isEqualTo(true);
	}

	@Test
	public void testConfigMapFromSingleInvalidPropertiesContent() {
		String configMapName = "single-unparseable-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", "somevalue").build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

		// no exception is thrown for unparseable content
	}

	@Test
	public void testConfigMapFromSingleInvalidYamlContent() {
		String configMapName = "single-unparseable-yaml-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.yaml", "somevalue").build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

		// no exception is thrown for unparseable content
	}

	@Test
	public void testConfigMapFromMultipleApplicationProperties() {
		String configMapName = "app-multiple-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", ConfigMapTestUtil.readResourceFile("application.properties"))
				.addToData("adhoc.properties", ConfigMapTestUtil.readResourceFile("adhoc.properties")).build();

		mockClient.configMaps().inNamespace("test").create(configMap);

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(mockClient, configMapName);

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
