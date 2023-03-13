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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingSecretsChangeDetector;
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
class ConfigReloadAutoConfigurationTest extends KubernetesConfigTestBase {

	private static final String APPLICATION_NAME = "application";

	// injected because of @EnableKubernetesMockClient. Because of the way
	// KubernetesMockServerExtension
	// injects this field (it searches for a static KubernetesClient field in the test
	// class), we can't have a common
	// class where this configuration is present.
	private static KubernetesClient mockClient;

	@BeforeAll
	static void setUpBeforeClass() {

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
		mockClient.configMaps().inNamespace("test").resource(configMap1).create();

		ConfigMap configMap2 = new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
				.addToData(data).build();
		mockClient.configMaps().inNamespace("spring").resource(configMap2).create();
	}

	@BeforeEach
	void beforeEach() {
		commonProperties = new String[] { "spring.cloud.bootstrap.enabled=true" };
	}

	@Test
	void kubernetesConfigReloadDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	void kubernetesConfigReloadWhenKubernetesConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false");
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	@Test
	void kubernetesConfigReloadWhenKubernetesDisabled() {
		setup(KubernetesClientTestConfiguration.class);
		assertThat(this.getContext().containsBean("configurationChangeDetector")).isFalse();
		assertThat(this.getContext().containsBean("configurationUpdateStrategy")).isFalse();
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true by default
	 *
	 *     - config map event watcher is picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsEnabledByDefault() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(Fabric8EventBasedConfigMapChangeDetector.class));
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true by default
	 *
	 *     - config map event watcher is picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=event", "spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(Fabric8EventBasedConfigMapChangeDetector.class));
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=event", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 0);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via poll reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitoringConfigMapsDisabledMonitoringSecretsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=polling", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 0);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via poll reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true by default
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitoringConfigMapsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=polling", "spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(
				map.values().iterator().next().getClass().isAssignableFrom(PollingConfigMapChangeDetector.class));
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is true
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsDisabledMonitoringSecretsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.mode=event");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(Fabric8EventBasedSecretsChangeDetector.class));
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is true
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitoringConfigMapsDisabledMonitoringSecretsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.mode=polling");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(
				map.values().iterator().next().getClass().isAssignableFrom(PollingSecretsChangeDetector.class));
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is true
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true
	 *
	 *     - config map event watcher is picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsEnabledMonitoringSecretsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.mode=event");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 2);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), Fabric8EventBasedConfigMapChangeDetector.class);
		Assertions.assertEquals(result.get(1).getClass(), Fabric8EventBasedSecretsChangeDetector.class);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is true
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitoringConfigMapsEnabledMonitoringSecretsEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.mode=polling");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 2);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), PollingConfigMapChangeDetector.class);
		Assertions.assertEquals(result.get(1).getClass(), PollingSecretsChangeDetector.class);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsDisabledMonitoringSecretsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=event", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 0);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via poll reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is false
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitorConfigMapsDisabledMonitoringSecretsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=polling", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 0);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via event reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is false
	 *
	 *     - config map event watcher is picked up
	 *     - config map polling watcher is not picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadEventEnabledMonitoringConfigMapsEnabledMonitoringSecretsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=event", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), Fabric8EventBasedConfigMapChangeDetector.class);
	}

	/**
	 * <pre>
	 *     - reload mode is enabled (via polling reload)
	 *     - spring.cloud.kubernetes.reload.monitoring-configMaps is true
	 *     - spring.cloud.kubernetes.reload.monitoring-secrets is false
	 *
	 *     - config map event watcher is not picked up
	 *     - config map polling watcher is picked up
	 *     - secrets event watcher is not picked up
	 *     - secrets polling watcher is not picked up
	 * </pre>
	 */
	@Test
	void reloadPollingEnabledMonitoringConfigMapsEnabledMonitoringSecretsDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=polling", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = getContext().getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), PollingConfigMapChangeDetector.class);
	}

	@Test
	void kubernetesReloadEnabledButSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=false",
				"spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.getContext().containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.getContext().containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	void kubernetesReloadEnabledButSecretAndConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.reload.enabled=true");
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
