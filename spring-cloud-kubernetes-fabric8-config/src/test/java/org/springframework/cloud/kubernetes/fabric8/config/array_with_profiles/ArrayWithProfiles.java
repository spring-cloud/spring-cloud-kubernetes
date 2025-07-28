/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.array_with_profiles;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.fabric8.config.TestApplication;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.kubernetes.fabric8.config.ConfigMapTestUtil.readResourceFile;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class,
		properties = { "spring.application.name=array-with-profiles", "spring.cloud.kubernetes.reload.enabled=false",
				"spring.main.cloud-platform=KUBERNETES" })
abstract class ArrayWithProfiles {

	private static final String APPLICATION_NAME = "array-with-profiles";

	static void setUpBeforeClass(KubernetesClient mockClient) {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		HashMap<String, String> data = new HashMap<>();
		data.put(Constants.APPLICATION_YML, readResourceFile("array-with-profiles.yaml"));
		mockClient.configMaps()
			.inNamespace("test")
			.resource(new ConfigMapBuilder().withNewMetadata()
				.withName(APPLICATION_NAME)
				.endMetadata()
				.addToData(data)
				.build())
			.create();
	}

}
