/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.sanitize_secrets;

import java.util.Base64;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author wind57
 */
abstract class Fabric8SecretsSanitize {

	private static final String NAMESPACE = "test";

	static void setUpBeforeClass(KubernetesClient mockClient) {

		// Configure kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, NAMESPACE);
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Secret secret = new SecretBuilder().withNewMetadata()
			.withName("sanitize-secret")
			.endMetadata()
			.addToData("sanitize.sanitizeSecretName",
					Base64.getEncoder().encodeToString("sanitizeSecretValue".getBytes()))
			.build();
		mockClient.secrets().inNamespace(NAMESPACE).resource(secret).create();

		Secret secretTwo = new SecretBuilder().withNewMetadata()
			.withName("sanitize-secret-two")
			.endMetadata()
			.addToData("sanitize.sanitizeSecretNameTwo",
					Base64.getEncoder().encodeToString("sanitizeSecretValueTwo".getBytes()))
			.build();
		mockClient.secrets().inNamespace(NAMESPACE).resource(secretTwo).create();

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
			.withName("sanitize-configmap")
			.endMetadata()
			.addToData("sanitize.sanitizeConfigMapName", "sanitizeConfigMapValue")
			.build();
		mockClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();

	}

}
