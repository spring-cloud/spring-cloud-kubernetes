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
package org.springframework.cloud.kubernetes.client.config.reload;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.cloud.kubernetes.client.config.KubernetesClientBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Haytham Mohamed
 **/

@ExtendWith(SpringExtension.class)
// thanks to the SmartClassLoader, it loads ApplicationContext from
// the static inner Configuration class
@ContextConfiguration
public class KubernetesClientConfigReloadAutoConfigurationTest {

	// from local configuration class
	@Autowired
	ConfigurableApplicationContext testContext;

	// will be created per test to alter context configuration
	private static ConfigurableApplicationContext context;

	@ClassRule
	public static WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

	protected void setup(String... env) {
		context = new SpringApplicationBuilder()
				.parent(testContext)
				.sibling(
						PropertyPlaceholderAutoConfiguration.class,
						LocalTestConfig.class,
						ConfigReloadAutoConfiguration.class,
						RefreshAutoConfiguration.class,
						EndpointAutoConfiguration.class,
						InfoEndpointAutoConfiguration.class,
						RefreshEndpointAutoConfiguration.class,
						ConfigurationPropertiesBindingPostProcessor.class,
						ConfigurationPropertiesRebinderAutoConfiguration.class,
						KubernetesClientBootstrapConfiguration.class,
						KubernetesBootstrapConfiguration.class,
						KubernetesClientConfigReloadAutoConfiguration.class
				)
				.web(org.springframework.boot.WebApplicationType.NONE)
				.properties(env).run();
	}

	@BeforeAll
	public static void startWireMockServer() {
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
	}

	@AfterEach
	public void afterEach() {
		if (this.context != null) {
			this.context.close();
		}
		WireMock.reset();
	}

	@BeforeEach
	public void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withName("test-cm").withNamespace("default")
										.withResourceVersion("1").build())
								.addToData("app.name", "test")
								.build());

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));
	}

	@Test
	public void kubernetesConfigReloadDisabled() throws Exception {
		setup("spring.cloud.kubernetes.reload.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	public void kubernetesConfigReloadWhenKubernetesConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	private void kubernetesConfigReloadWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretAndConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false",
				"spring.cloud.kubernetes.reload.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	public void kubernetesReloadEnabledWithPolling() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true",
				"spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.reload.mode=polling");
		assertThat(context.containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isTrue();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	public void kubernetesReloadEnabledWithEvent() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
			"spring.cloud.kubernetes.config.enabled=true",
			"spring.cloud.kubernetes.secrets.enabled=true",
			"spring.cloud.kubernetes.reload.enabled=true",
			"spring.cloud.kubernetes.client.namespace=default",
			"spring.cloud.kubernetes.reload.mode=event");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isTrue();
	}

	@Test
	public void kubernetesReloadDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.reload.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isFalse();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
			"spring.cloud.kubernetes.config.enabled=true",
			"spring.cloud.kubernetes.secrets.enabled=false",
			"spring.cloud.kubernetes.reload.enabled=true",
			"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertySourceLocator"))
				.isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true",
				"spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.client.namespace=default");
		assertThat(context.containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertySourceLocator"))
				.isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@AutoConfigureBefore(KubernetesClientAutoConfiguration.class)
	static class LocalTestConfig {

		@ConditionalOnMissingBean(KubernetesClientProperties.class)
		@Bean
		KubernetesClientProperties kubernetesClientProperties() {
			KubernetesClientProperties properties = new KubernetesClientProperties();
			properties.setNamespace("default");
			return properties;
		}

		@ConditionalOnMissingBean(ApiClient.class)
		@Bean
		ApiClient apiClient() {
			ApiClient apiClient =
					new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
			apiClient.setDebugging(true);
			return apiClient;
		}

		@ConditionalOnMissingBean(CoreV1Api.class)
		@Bean
		CoreV1Api coreApi(ApiClient apiClient) {
			System.out.println("Yahooooo");////
			return new CoreV1Api(apiClient);
		}
	}

}
