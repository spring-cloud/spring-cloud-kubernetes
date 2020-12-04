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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
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
//@SpringBootTest(properties = "spring.cloud.kubernetes.client.namespace=default")
@ContextConfiguration(classes =
		KubernetesClientConfigReloadAutoConfigurationTest.TestConfig.class)
public class KubernetesClientConfigReloadAutoConfigurationTest {

	@Autowired
	ApplicationContext context;

	@ClassRule
	public static WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	@BeforeEach
	public void beforeEach() {
		WireMock.configureFor(wireMockServer.port());
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList()
				.addItemsItem(
						new V1ConfigMapBuilder()
								.withMetadata(new V1ObjectMetaBuilder().withName("bootstrap-640").withNamespace("default")
										.withResourceVersion("1").build())
								.addToData("app.name", "test")
								.build());

		String url = "^/api/v1/namespaces/default/configmaps.*";
		WireMock.stubFor(get(urlMatching(url))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));
	}

	@Test
	public void kubernetesConfigReloadDisabled() throws Exception {
		System.setProperty("spring.cloud.kubernetes.reload.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
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
		System.setProperty("spring.cloud.kubernetes.config.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
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
	public void kubernetesConfigReloadWhenKubernetesDisabled() throws Exception {
		System.setProperty("spring.cloud.kubernetes.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
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
		System.setProperty("spring.cloud.kubernetes.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.config.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.secrets.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.reload.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
		assertThat(context.containsBean("configMapPropertySourceLocator"))
				.isFalse();
		assertThat(context.containsBean("secretsPropertySourceLocator"))
				.isFalse();
		assertThat(context.containsBean("propertyChangeWatcher")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabled() throws Exception {
		System.setProperty("spring.cloud.kubernetes.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.config.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.secrets.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.reload.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
		assertThat(context.containsBean("configMapPropertyChangePollingWatcher"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertyChangePollingWatcher"))
				.isTrue();
		assertThat(context.containsBean("configMapPropertyChangeEventWatcher"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertyChangeEventWatcher"))
				.isTrue();
	}

	@Test
	public void kubernetesReloadEnabledButSecretDisabled() throws Exception {
		System.setProperty("spring.cloud.kubernetes.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.config.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.secrets.enabled", "false");
		System.setProperty("spring.cloud.kubernetes.reload.enabled", "true");
		System.setProperty("spring.cloud.kubernetes.client.namespace", "default");
		assertThat(context.containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(context.containsBean("secretsPropertySourceLocator"))
				.isFalse();
		assertThat(context.containsBean("propertyChangeWatcher")).isTrue();
	}

	@AutoConfigureBefore(KubernetesClientAutoConfiguration.class)
	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@ConditionalOnMissingBean(ApiClient.class)
		ApiClient testingApiClient() {
			wireMockServer.start();
			ApiClient apiClient =
					new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
			apiClient.setDebugging(true);
			io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
			return apiClient;
		}

		@Bean
		@ConditionalOnMissingBean(CoreV1Api.class)
		CoreV1Api k8sApi(ApiClient apiClient) {
			return new CoreV1Api(apiClient);
		}
	}

}
