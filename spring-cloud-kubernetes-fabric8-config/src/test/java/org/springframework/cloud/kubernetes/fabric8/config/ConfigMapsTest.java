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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Charles Moulliard
 */
@EnableKubernetesMockClient(crud = true, https = false)
class ConfigMapsTest {

	private static KubernetesClient mockClient;

	@AfterEach
	void afterEach() {
		new Fabric8ConfigMapsCache().discardAll();
	}

	@Test
	public void testConfigMapList() {
		mockClient.configMaps().inNamespace("ns1")
				.resource(new ConfigMapBuilder().withNewMetadata().withName("empty").endMetadata().build()).create();

		ConfigMapList configMapList = mockClient.configMaps().inNamespace("ns1").list();
		assertThat(configMapList).isNotNull();
		// metadata is an element
		assertThat(configMapList.getItems().size()).isEqualTo(1);
		assertThat(configMapList.getItems().get(0).getData()).isEmpty();
	}

	@Test
	void testConfigMapGet() {

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName("reload-example").endMetadata()
				.addToData("KEY", "123").build();

		mockClient.configMaps().inNamespace("ns2").resource(configMap).create();

		ConfigMapList configMapList = mockClient.configMaps().inNamespace("ns2").list();
		assertThat(configMapList).isNotNull();
		assertThat(configMapList.getItems().size()).isEqualTo(1);
		assertThat(configMapList.getItems().get(0).getData().size()).isEqualTo(1);
		Map<String, String> resultData = configMapList.getItems().get(0).getData();
		assertThat(resultData.get("KEY")).isEqualTo("123");
	}

	@Test
	void testConfigMapFromSingleApplicationProperties() {
		String configMapName = "app-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", ConfigMapTestUtil.readResourceFile("application.properties"))
				.build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "test", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

		assertThat(cmps.getProperty("dummy.property.string1")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int1")).isEqualTo("1");
		assertThat(cmps.getProperty("dummy.property.bool1")).isEqualTo("true");
	}

	@Test
	void testConfigMapFromSingleApplicationYaml() {
		String configMapName = "app-yaml-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.yaml", ConfigMapTestUtil.readResourceFile("application.yaml")).build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "test", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

		assertThat(cmps.getProperty("dummy.property.string2")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int2")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool2")).isEqualTo(true);
	}

	@Test
	void testConfigMapFromSingleNonStandardFileName() {
		String configMapName = "single-non-standard-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("adhoc.yml", ConfigMapTestUtil.readResourceFile("adhoc.yml")).build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "test", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

		assertThat(cmps.getProperty("dummy.property.string3")).isEqualTo("a");
		assertThat(cmps.getProperty("dummy.property.int3")).isEqualTo(1);
		assertThat(cmps.getProperty("dummy.property.bool3")).isEqualTo(true);
	}

	@Test
	void testConfigMapFromSingleInvalidPropertiesContent() {
		String configMapName = "single-unparseable-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", "somevalue").build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "namespace", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

		// no exception is thrown for unparseable content
	}

	@Test
	void testConfigMapFromSingleInvalidYamlContent() {
		String configMapName = "single-unparseable-yaml-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.yaml", "somevalue").build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "namespace", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

		// no exception is thrown for unparseable content
	}

	@Test
	void testConfigMapFromMultipleApplicationProperties() {
		String configMapName = "app-multiple-properties-test";
		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
				.addToData("application.properties", ConfigMapTestUtil.readResourceFile("application.properties"))
				.addToData("adhoc.properties", ConfigMapTestUtil.readResourceFile("adhoc.properties")).build();

		mockClient.configMaps().inNamespace("test").resource(configMap).create();
		NormalizedSource source = new NamedConfigMapNormalizedSource(configMapName, "test", false, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(mockClient, source, "", new MockEnvironment());

		Fabric8ConfigMapPropertySource cmps = new Fabric8ConfigMapPropertySource(context);

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
