/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:cmoullia@redhat.com">Charles Moulliard</a>
 */
public class ConfigMapsTest {

	@Rule
	public KubernetesServer server = new KubernetesServer();

	@Test
	public void testConfigMapList() {
		server.expect().withPath("/api/v1/namespaces/ns1/configmaps").andReturn(200, new ConfigMapListBuilder().build()).once();

		KubernetesClient client = server.getClient();

		ConfigMapList configMapList = client.configMaps().inNamespace("ns1").list();
		assertNotNull(configMapList);
		assertEquals(0, configMapList.getItems().size());
	}

	@Test
	public void testConfigMapGet() {
		server.expect().withPath("/api/v1/namespaces/ns2/configmaps").andReturn(200, new ConfigMapBuilder()
			.withNewMetadata().withName("reload-example").endMetadata()
			.addToData("KEY","123")
			.build())
			.once();

		KubernetesClient client = server.getClient();
		ConfigMapList configMapList = client.configMaps().inNamespace("ns2").list();
		assertNotNull(configMapList);
		assertEquals(1, configMapList.getAdditionalProperties().size());
		@SuppressWarnings("unchecked")
		Map<String,String> data = (Map<String, String>) configMapList.getAdditionalProperties().get("data");
		assertEquals("123",data.get("KEY"));
	}

	@Test
	public void testConfigMapGetFromVolume() throws IOException {
		KubernetesClient client = server.getClient();
		ConfigMapConfigProperties cmConfProperties = new ConfigMapConfigProperties();
		cmConfProperties.setEnableApi(false);

		// create test data, as if in-container volumes mounted by k8s, see
		// https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#add-configmap-data-to-a-volume
		final Path tmp = Files.createTempDirectory("test-k8s-cm-");
		final Path dbPath = tmp.resolve("cm/db");
		final Path apiPath = tmp.resolve("cm/api");
		createConfigMapFile(dbPath, "db.url", "http://localhost/db");
		createConfigMapFile(apiPath, "api.url", "http://localhost/api");
		createConfigMapFile(apiPath, "foo.bar", "42");

		// parse ConfigMaps
		cmConfProperties.setPaths(Arrays.asList(dbPath.toString(), apiPath.toString()));
		ConfigMapPropertySource cmps = new ConfigMapPropertySource(client, "testapp", cmConfProperties);

		// assert as expected
		assertEquals("42", cmps.getProperty("foo.bar"));
		assertEquals("http://localhost/db", cmps.getProperty("db.url"));
		assertEquals("http://localhost/api", cmps.getProperty("api.url"));
		assertFalse(cmps.containsProperty("no.such.property"));

		FileSystemUtils.deleteRecursively(tmp.toFile());
	}

	private void createConfigMapFile(Path basePath, String key, String value) throws IOException {
		Files.createDirectories(basePath);
		final Path apiUrlFile = Files.createFile(basePath.resolve(key));
		Files.write(apiUrlFile, value.getBytes(StandardCharsets.UTF_8));
	}

}
