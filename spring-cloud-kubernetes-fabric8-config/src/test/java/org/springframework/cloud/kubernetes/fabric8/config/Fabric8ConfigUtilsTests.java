/*
 * Copyright 2013-present the original author or authors.
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

import java.time.Duration;
import java.util.Set;

import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ConfigUtilsTests {

	@Test
	void testNamespacesFromProperties() {
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties(false, true, false,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.EVENT,
				Duration.ofMillis(15000), Set.of("non-default"), false, Duration.ofSeconds(2));
		Set<String> namespaces = Fabric8ConfigUtils.namespaces(null,
				new KubernetesNamespaceProvider(new MockEnvironment()), configReloadProperties, "configmap");
		Assertions.assertThat(namespaces.size()).isEqualTo(1);
		Assertions.assertThat(namespaces.iterator().next()).isEqualTo("non-default");
	}

	@Test
	void testNamespacesFromProvider() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.client.namespace", "some");
		KubernetesNamespaceProvider provider = new KubernetesNamespaceProvider(environment);
		Set<String> namespaces = Fabric8ConfigUtils.namespaces(null, provider, ConfigReloadProperties.DEFAULT,
				"configmap");
		Assertions.assertThat(namespaces.size()).isEqualTo(1);
		Assertions.assertThat(namespaces.iterator().next()).isEqualTo("some");
	}

}
