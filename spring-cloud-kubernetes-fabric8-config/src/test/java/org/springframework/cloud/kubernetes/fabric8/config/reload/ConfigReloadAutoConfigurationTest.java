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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.fabric8.config.KubernetesConfigTestBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Haytham Mohamed
 *
 * To test if either kubernetes, kubernetes.configmap, or reload is disabled, then the
 * detector and update strategy beans won't be available.
 **/
@EnableKubernetesMockClient(crud = true, https = false)
public class ConfigReloadAutoConfigurationTest extends KubernetesConfigTestBase {

	private static final String APPLICATION_NAME = "application";

	// injected because of @EnableKubernetesMockClient. Because of the way
	// KubernetesMockServerExtension
	// injects this field (it searches for a static KubernetesClient field in the test
	// class), we can't have a common
	// class where this configuration is present.
	private static KubernetesClient mockClient;

	@BeforeAll
	public static void setUpBeforeClass() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		HashMap<String, String> data = new HashMap<>();
		data.put("bean.greeting", "Hello ConfigMap, %s!");

		ConfigMap configMap1 = new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
				.addToData(data).build();
		mockClient.configMaps().inNamespace("test").create(configMap1);

		ConfigMap configMap2 = new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
				.addToData(data).build();
		mockClient.configMaps().inNamespace("spring").create(configMap2);
	}

	@Test
	public void kubernetesConfigReloadDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesConfigReloadWhenKubernetesConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesConfigReloadWhenKubernetesDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=true",
				"spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertyChangeEventWatcher")).isTrue();
	}

	@Test
	public void kubernetesReloadEnabledButSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=false",
				"spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretAndConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=false",
				"spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("propertyChangeWatcher")).isFalse();
	}

	@Configuration(proxyBeanMethods = false)
	private static class KubernetesClientTestConfiguration {

		@ConditionalOnMissingBean(KubernetesClient.class)
		@Bean
		KubernetesClient kubernetesClient() {
			return mockClient;
		}

	}

}
