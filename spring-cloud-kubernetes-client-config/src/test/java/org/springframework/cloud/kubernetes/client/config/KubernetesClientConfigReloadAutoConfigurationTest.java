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

package org.springframework.cloud.kubernetes.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingSecretsChangeDetector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Haytham Mohamed
 **/

public class KubernetesClientConfigReloadAutoConfigurationTest {

	private ConfigurableApplicationContext context;

	public static WireMockServer wireMockServer;

	protected void setup(String... env) {
		List<String> envList = (env != null) ? new ArrayList<>(Arrays.asList(env)) : new ArrayList<>();
		envList.add("spring.cloud.kubernetes.client.namespace=default");
		String[] envArray = envList.toArray(new String[0]);

		context = new SpringApplicationBuilder(PropertyPlaceholderAutoConfiguration.class, LocalTestConfig.class,
				ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class, EndpointAutoConfiguration.class,
				InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
				ConfigurationPropertiesBindingPostProcessor.class, ConfigReloadPropertiesAutoConfiguration.class,
				ConfigurationPropertiesRebinderAutoConfiguration.class, KubernetesClientBootstrapConfiguration.class,
				KubernetesBootstrapConfiguration.class, KubernetesClientConfigReloadAutoConfiguration.class)
						.web(org.springframework.boot.WebApplicationType.NONE).properties(envArray).run();
	}

	@BeforeAll
	static void startWireMockServer() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
	}

	@AfterEach
	void afterEach() {
		if (this.context != null) {
			this.context.close();
			this.context = null;
		}
	}

	@BeforeEach
	void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList().addItemsItem(new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName("test-cm").withNamespace("default").withResourceVersion("1").build())
				.addToData("app.name", "test").build());

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));
	}

	// 1. watchers

	@Test
	void kubernetesWatchersWhenKubernetesDisabled() {
		setup();
		assertThat(context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(context.containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	void kubernetesWatchersWhenConfigDisabled() {
		setup("spring.cloud.kubernetes.config.enabled=false");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	void kubernetesWatchersWhenReloadDisabled() {
		setup("spring.cloud.kubernetes.reload.enabled=false");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	void kubernetesReloadEnabledButSecretAndConfigDisabled() {
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher")).isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(KubernetesClientEventBasedConfigMapChangeDetector.class));
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=event",
				"spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(KubernetesClientEventBasedConfigMapChangeDetector.class));
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=event",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=polling",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=polling",
				"spring.main.cloud-platform=KUBERNETES");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.mode=event");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		Assertions.assertTrue(map.values().iterator().next().getClass()
				.isAssignableFrom(KubernetesClientEventBasedSecretsChangeDetector.class));
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.mode=polling");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.mode=event");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 2);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), KubernetesClientEventBasedConfigMapChangeDetector.class);
		Assertions.assertEquals(result.get(1).getClass(), KubernetesClientEventBasedSecretsChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.reload.monitoring-secrets=true",
				"spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.mode=polling");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=event",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=polling",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=false",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=event",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), KubernetesClientEventBasedConfigMapChangeDetector.class);
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
		setup("spring.cloud.kubernetes.reload.enabled=true", "spring.cloud.kubernetes.reload.mode=polling",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.monitoring-configMaps=true",
				"spring.cloud.kubernetes.reload.monitoring-secrets=false");
		Map<String, ConfigurationChangeDetector> map = context.getBeansOfType(ConfigurationChangeDetector.class);
		Assertions.assertEquals(map.size(), 1);
		List<ConfigurationChangeDetector> result = map.values().stream()
				.sorted(Comparator.comparing(x -> x.getClass().getName())).toList();
		Assertions.assertEquals(result.get(0).getClass(), PollingConfigMapChangeDetector.class);
	}

	// 2. config and secrets property source locators

	@Test
	void kubernetesConfigAndSecretEnabledByDefault() {
		setup("spring.main.cloud-platform=KUBERNETES");
		assertThat(context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(context.containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesConfigEnabledButSecretDisabled() {
		setup("spring.cloud.kubernetes.secrets.enabled=false", "spring.main.cloud-platform=KUBERNETES");
		assertThat(context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(context.containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	void kubernetesSecretsEnabledButConfigDisabled() {
		setup("spring.cloud.kubernetes.config.enabled=false", "spring.main.cloud-platform=KUBERNETES");
		assertThat(context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(context.containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@AutoConfigureBefore(KubernetesClientAutoConfiguration.class)
	static class LocalTestConfig {

		@ConditionalOnMissingBean(KubernetesClientProperties.class)
		@Bean
		KubernetesClientProperties kubernetesClientProperties() {
			return new KubernetesClientProperties(null, null, null, "default", null, null, null, null, null, null, null,
					null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
					null);
		}

		@Bean
		ApiClient apiClient() {
			ApiClient apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
			apiClient.setDebugging(true);
			apiClient.setReadTimeout(0);
			return apiClient;
		}

		@Bean
		CoreV1Api coreApi(ApiClient apiClient) {
			return new CoreV1Api(apiClient);
		}

	}

}
