/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapContextClosedEvent;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientBootstrapConfiguration;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.kubernetes.client.namespace=default", "spring.profiles.include=kubernetes",
				"spring.cloud.kubernetes.secrets.enableApi=true", "debug=true" },
		classes = { KubernetesConfigServerApplication.class })
public class ConfigServerIntegrationTest {

	@Autowired
	private ConfigurableApplicationContext context;

	@Autowired
	private TestRestTemplate testRestTemplate;

	public static WireMockServer wireMockServer;

	protected void setup(String... env) {
		List<String> envList = (env != null) ? new ArrayList(Arrays.asList(env)) : new ArrayList<>();
		envList.add("spring.cloud.kubernetes.client.namespace=default");
		envList.add("spring.profiles.include=kubernetes");
		String[] envArray = envList.stream().toArray(String[]::new);

		context = new SpringApplicationBuilder(PropertyPlaceholderAutoConfiguration.class, LocalTestConfig.class,
				ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class, EndpointAutoConfiguration.class,
				InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
				ConfigurationPropertiesBindingPostProcessor.class,
				ConfigurationPropertiesRebinderAutoConfiguration.class, KubernetesClientBootstrapConfiguration.class,
				KubernetesBootstrapConfiguration.class, KubernetesClientConfigReloadAutoConfiguration.class,
				KubernetesConfigServerAutoConfiguration.class)
						.addBootstrapRegistryInitializer(new BootstrapRegistryInitializer() {
							@Override
							public void initialize(BootstrapRegistry registry) {
								System.out.println(registry.isRegistered(ApiClient.class));
								registry.addCloseListener(new ApplicationListener<BootstrapContextClosedEvent>() {
									@Override
									public void onApplicationEvent(BootstrapContextClosedEvent event) {
										ApiClient apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl())
												.build();
										apiClient.setDebugging(true);
										event.getApplicationContext().getBeanFactory().registerSingleton("apiClient",
												apiClient);
									}
								});
								registry.register(ApiClient.class, new BootstrapRegistry.InstanceSupplier<ApiClient>() {
									@Override
									public ApiClient get(BootstrapContext context) {
										ApiClient apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl())
												.build();
										apiClient.setDebugging(true);
										return apiClient;
									}
								});
							}
						}).web(WebApplicationType.NONE).properties(envArray).run();
	}

	// @BeforeAll
	// public static void startWireMockServer() {
	// wireMockServer = new WireMockServer(options().dynamicPort());
	//
	// wireMockServer.start();
	// WireMock.configureFor(wireMockServer.port());
	// }

	// @AfterEach
	// public void afterEach() {
	// if (this.context != null) {
	// this.context.close();
	// this.context = null;
	// }
	// }

	@BeforeEach
	public void beforeEach() {
		V1ConfigMapList TEST_CONFIGMAP = new V1ConfigMapList().addItemsItem(new V1ConfigMapBuilder().withMetadata(
				new V1ObjectMetaBuilder().withName("test-cm").withNamespace("default").withResourceVersion("1").build())
				.addToData("app.name", "test").build());

		V1SecretList TEST_SECRET = new V1SecretListBuilder()
				.withMetadata(new V1ListMetaBuilder().withResourceVersion("1").build())
				.addToItems(new V1SecretBuilder()
						.withMetadata(new V1ObjectMetaBuilder().withName("test-cm").withResourceVersion("0")
								.withNamespace("default").build())
						.addToData("password", "p455w0rd".getBytes()).addToData("username", "user".getBytes()).build())
				.build();

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/configmaps.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_CONFIGMAP))));

		WireMock.stubFor(get(urlMatching("^/api/v1/namespaces/default/secrets.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(TEST_SECRET))));
	}

	// @Test
	// public void kubernetesDisabled() throws Exception {
	// setup("spring.cloud.kubernetes.enabled=false");
	// assertThat(context.containsBean("kubernetesEnvironmentRepository")).isFalse();
	// }

	@Test
	public void enabled() throws Exception {
		// setup("spring.cloud.kubernetes.secrets.enabled=true");
		Environment env = testRestTemplate.getForObject("/test-cm/default", Environment.class);
		assertThat(env.getPropertySources().size()).isEqualTo(2);
		assertThat(env.getPropertySources().get(0).getName().equals("configmap.test-cm.default")).isTrue();
		assertThat(env.getPropertySources().get(0).getSource().get("app.name")).isEqualTo("test");
		assertThat(env.getPropertySources().get(1).getName().equals("secrets.test-cm.default")).isTrue();
		assertThat(env.getPropertySources().get(1).getSource().get("password")).isEqualTo("p455w0rd");
		assertThat(env.getPropertySources().get(1).getSource().get("username")).isEqualTo("user");
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
			ApiClient apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
			apiClient.setDebugging(true);
			return apiClient;
		}

		@ConditionalOnMissingBean(CoreV1Api.class)
		@Bean
		CoreV1Api coreApi(ApiClient apiClient) {
			return new CoreV1Api(apiClient);
		}

	}

}
