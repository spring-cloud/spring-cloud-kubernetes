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

package org.springframework.cloud.kubernetes.client.discovery;

import java.io.StringReader;
import java.util.Collections;
import java.util.concurrent.Semaphore;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertNonSelectiveNamespacesBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertNonSelectiveNamespacesBeansPresent;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertSelectiveNamespacesBeansMissing;
import static org.springframework.cloud.kubernetes.client.discovery.TestUtils.assertSelectiveNamespacesBeansPresent;

/**
 * Test various conditionals for
 * {@link KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesClientInformerReactiveDiscoveryClientAutoConfigurationApplicationContextTests {

	private static final Parameters GET_ENDPOINTS_PARAMETERS = new Parameters();

	private static final Semaphore GET_ENDPOINTS_SEMAPHORE = new Semaphore(1);

	private static final String GET_ENDPOINTS_PARAMETER_NAME = "get-endpoints";

	@RegisterExtension
	private static final WireMockExtension API_SERVER =
		WireMockExtension.newInstance()
			.options(options().dynamicPort().extensions(new PostServeExtension()))
			.build();

	@BeforeEach
	void beforeAll() throws InterruptedException {
		mockEndpointsCall();
		mockServicesCall();
	}

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);

			// simple from commons and ours
			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
			assertThat(context).hasBean("reactiveIndicatorInitializer");

			assertNonSelectiveNamespacesBeansPresent(context);
			assertSelectiveNamespacesBeansMissing(context);
		});
	}

//	@Test
//	void discoveryEnabledDefaultWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.kubernetes.discovery.namespaces=a,b,c");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 3);
//		});
//	}
//
//	@Test
//	void discoveryEnabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.enabled=true");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void discoveryEnabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b,c");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 3);
//		});
//	}
//
//	@Test
//	void discoveryDisabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void discoveryDisabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b,c");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesDiscoveryEnabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.kubernetes.discovery.enabled=true");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesDiscoveryEnabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.kubernetes.discovery.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}
//
//	@Test
//	void kubernetesDiscoveryDisabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.kubernetes.discovery.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			// only "simple" one from commons, as ours is not picked up
//			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesDiscoveryDisabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.kubernetes.discovery.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			// only "simple" one from commons, as ours is not picked up
//			assertThat(context).hasSingleBean(ReactiveDiscoveryClientHealthIndicator.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesReactiveDiscoveryEnabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.reactive.enabled=true");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesReactiveDiscoveryEnabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.reactive.enabled=true", "spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}
//
//	@Test
//	void kubernetesReactiveDiscoveryDisabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.reactive.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void kubernetesReactiveDiscoveryDisabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.reactive.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).doesNotHaveBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}
//
//	/**
//	 * blocking is disabled, and it should not impact reactive in any way.
//	 */
//	@Test
//	void blockingDisabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.blocking.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	/**
//	 * blocking is disabled, and it should not impact reactive in any way.
//	 */
//	@Test
//	void blockingDisabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.blocking.enabled=false", "spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			// simple from commons and ours
//			assertThat(context).getBeans(ReactiveDiscoveryClientHealthIndicator.class).hasSize(2);
//			assertThat(context).hasSingleBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//			assertThat(context).hasBean("reactiveIndicatorInitializer");
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}
//
//	@Test
//	void healthDisabled() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.client.health-indicator.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void healthDisabledWithSelectiveNamespaces() {
//		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.client.health-indicator.enabled=false",
//				"spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}
//
//	@Test
//	void healthEnabledClassNotPresent() {
//		setupWithFilteredClassLoader("org.springframework.boot.health.contributor.ReactiveHealthIndicator",
//				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.client.health-indicator.enabled=false");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("kubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansPresent(context);
//			assertSelectiveNamespacesBeansMissing(context);
//		});
//	}
//
//	@Test
//	void healthEnabledClassNotPresentWithSelectiveNamespaces() {
//		setupWithFilteredClassLoader("org.springframework.boot.health.contributor.ReactiveHealthIndicator",
//				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
//				"spring.cloud.discovery.client.health-indicator.enabled=false",
//				"spring.cloud.kubernetes.discovery.namespaces=a,b");
//		applicationContextRunner.run(context -> {
//			assertThat(context).hasSingleBean(KubernetesClientInformerDiscoveryClient.class);
//			assertThat(context).hasBean("selectiveNamespacesKubernetesClientInformerDiscoveryClient");
//			assertThat(context).hasSingleBean(KubernetesClientInformerReactiveDiscoveryClient.class);
//
//			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClientHealthIndicator.class);
//			assertThat(context).doesNotHaveBean(KubernetesDiscoveryClientHealthIndicatorInitializer.class);
//
//			assertNonSelectiveNamespacesBeansMissing(context);
//			assertSelectiveNamespacesBeansPresent(context, 2);
//		});
//	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
					UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class,
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class))
			.withUserConfiguration(ApiClientConfig.class)
			.withPropertyValues(properties);
	}

	private void setupWithFilteredClassLoader(String name, String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, SimpleReactiveDiscoveryClientAutoConfiguration.class,
					UtilAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
					KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class,
					KubernetesCommonsAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class,
					KubernetesClientDiscoveryClientSpelAutoConfiguration.class))
			//.withUserConfiguration(ApiClientConfig.class)
			.withClassLoader(new FilteredClassLoader(name))
			.withPropertyValues(properties);
	}

	@Configuration
	static class ApiClientConfig {

		@Bean
		@Primary
		ApiClient apiClient() throws Exception {
			return new ClientBuilder().setBasePath("http://localhost:" + API_SERVER.getPort()).build();
		}

	}

	private static void mockEndpointsCall() {

		// watch=false, first call to populate watcher cache
		API_SERVER.stubFor(
			WireMock.get(WireMock.urlMatching("^/api/v1/namespaces/default/endpoints.*"))
				.withQueryParam("watch", WireMock.equalTo("false"))
				.willReturn(
					WireMock.aResponse()
						.withStatus(200)
						.withBody(
							JSON.serialize(
								new V1EndpointsList()
									.metadata(new V1ListMeta().resourceVersion("0"))
									.addItemsItem(new V1Endpoints().metadata(new V1ObjectMeta().namespace("default")))
							))));

		// watch=true, call to re-sync
		API_SERVER.stubFor(WireMock.get(WireMock.urlMatching("^/api/v1/namespaces/default/endpoints.*"))
			.withQueryParam("watch", WireMock.equalTo("true"))
			.willReturn(aResponse().withStatus(200).withBody("{}")));
	}

	private static void mockServicesCall() throws InterruptedException {

		// watch=false, first call to populate watcher cache
		API_SERVER.stubFor(
			WireMock.get(WireMock.urlMatching("^/api/v1/namespaces/default/services.*"))
				.withQueryParam("watch", equalTo("false"))
				.willReturn(
					WireMock.aResponse()
						.withStatus(200)
						.withBody(
							JSON.serialize(
								new V1ServiceList()
									.metadata(new V1ListMeta().resourceVersion("0"))
									.addItemsItem(new V1Service().metadata(new V1ObjectMeta().namespace("default")))
							))));

		GET_ENDPOINTS_SEMAPHORE.acquire(1);
		GET_ENDPOINTS_PARAMETERS.put(GET_ENDPOINTS_PARAMETER_NAME, GET_ENDPOINTS_SEMAPHORE);
		// watch=true, call to re-sync
		API_SERVER.stubFor(
			WireMock.get(WireMock.urlMatching("^/api/v1/namespaces/default/services.*"))
				.withPostServeAction("PostServeExtension", GET_ENDPOINTS_PARAMETERS)
				.withQueryParam("watch", equalTo("true"))
				.willReturn(aResponse().withStatus(200).withBody("{}")));
	}

	private static final class PostServeExtension implements ServeEventListener {

		@Override
		public String getName() {
			return "PostServeExtension";
		}

		@Override
		public void afterMatch(ServeEvent serveEvent, Parameters parameters) {
			Object getEndpointsSemaphore = parameters.get(GET_ENDPOINTS_PARAMETER_NAME);
			if (getEndpointsSemaphore != null) {
				Semaphore semaphore = (Semaphore) getEndpointsSemaphore;
				semaphore.release();
			}
		}
	}

}
