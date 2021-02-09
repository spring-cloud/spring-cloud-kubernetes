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

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.cloud.kubernetes.fabric8.config.KubernetesConfigTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Haytham Mohamed
 *
 * To test if either kubernetes, kubernetes.configmap, or reload is disabled, then the
 * detector and update strategy beans won't be available.
 **/

public class ConfigReloadAutoConfigurationTest extends KubernetesConfigTestBase {

	private static final String APPLICATION_NAME = "application";

	@BeforeClass
	public static void setUpBeforeClass() {

		setup();
		KubernetesClient mockClient = getContext().getBean(KubernetesClient.class);

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		HashMap<String, String> data = new HashMap<>();
		data.put("bean.greeting", "Hello ConfigMap, %s!");
		server.expect().withPath("/api/v1/namespaces/test/configmaps/" + APPLICATION_NAME)
				.andReturn(200, new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
						.addToData(data).build())
				.always();
		server.expect().withPath("/api/v1/namespaces/spring/configmaps/" + APPLICATION_NAME)
				.andReturn(200, new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
						.addToData(data).build())
				.always();
	}

	@Test
	public void kubernetesConfigReloadDisabled() throws Exception {
		setup("spring.cloud.kubernetes.reload.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesConfigReloadWhenKubernetesConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesConfigReloadWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertyChangeEventWatcher")).isTrue();
	}

	@Test
	public void kubernetesReloadEnabledButSecretDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretAndConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("propertyChangeWatcher")).isFalse();
	}

}
