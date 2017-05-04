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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
		HashMap<String,String> data = (HashMap<String, String>) configMapList.getAdditionalProperties().get("data");
		assertEquals("123",data.get("KEY"));

	}

}
