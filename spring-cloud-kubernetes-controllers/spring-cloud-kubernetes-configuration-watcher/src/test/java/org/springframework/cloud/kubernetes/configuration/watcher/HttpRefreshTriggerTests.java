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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
@ExtendWith(MockitoExtension.class)
class HttpRefreshTriggerTests {

	private WireMockServer wireMockServer;

	@Mock
	private KubernetesClientInformerReactiveDiscoveryClient reactiveDiscoveryClient;

	@BeforeEach
	void beforeEach() {
		wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());
	}

	@AfterEach
	void afterEach() {
		wireMockServer.stop();
	}

	/**
	 * <pre>
	 *     - there are two services : app-one, app-two
	 *     - app-one has labels app=demo, tier=backend
	 *     - app-two has labels app=demo, tier=frontend
	 *     - we only search against "inputLabels" : { "app", "demo", "tier", "backend" }
	 *     - so only one serviceInstance triggers a refresh
	 * </pre>
	 */
	@Test
	void refreshesOnlyServiceInstancesMatchingLabels() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		HttpRefreshTrigger refreshTrigger = new HttpRefreshTrigger(reactiveDiscoveryClient, properties,
				WebClient.builder().build());

		when(reactiveDiscoveryClient.getServices()).thenReturn(Flux.just("app-one", "app-two"));
		when(reactiveDiscoveryClient.getInstances(eq("app-one")))
			.thenReturn(Flux.just(serviceInstance("app-one", "/app-one/actuator",
					Map.of("app", "demo", "tier", "backend"))));
		when(reactiveDiscoveryClient.getInstances(eq("app-two")))
			.thenReturn(Flux.just(serviceInstance("app-two", "/app-two/actuator",
					Map.of("app", "demo", "tier", "frontend"))));

		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/app-one/actuator/refresh"))
			.willReturn(WireMock.aResponse().withStatus(200)));
		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/app-two/actuator/refresh"))
			.willReturn(WireMock.aResponse().withStatus(200)));

		Map<String, String> inputLabels = Map.of("app", "demo", "tier", "backend");
		KubernetesSource source = new ConfigMapKubernetesSource(Set.of("my-configmap"), inputLabels, "my-configmap");

		// subscribe and assert that it did not fail
		StepVerifier.create(refreshTrigger.triggerRefresh(source)).verifyComplete();

		WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/app-one/actuator/refresh")));
		WireMock.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/app-two/actuator/refresh")));
	}

	/**
	 * <pre>
	 *     - input labels are empty
	 *     - we search directly by service name
	 *     - discovery getServices is not used
	 * </pre>
	 */
	@Test
	void refreshesServiceInstancesByNameWhenLabelsAreMissing() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		HttpRefreshTrigger refreshTrigger = new HttpRefreshTrigger(reactiveDiscoveryClient, properties,
				WebClient.builder().build());

		when(reactiveDiscoveryClient.getInstances(eq("app-one")))
			.thenReturn(Flux.just(serviceInstance("app-one", "/app-one/actuator", Map.of())));

		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/app-one/actuator/refresh"))
			.willReturn(WireMock.aResponse().withStatus(200)));

		KubernetesSource source = new ConfigMapKubernetesSource(Set.of("app-one"), Map.of(), "my-configmap");

		StepVerifier.create(refreshTrigger.triggerRefresh(source)).verifyComplete();

		WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/app-one/actuator/refresh")));
		verify(reactiveDiscoveryClient, never()).getServices();
	}

	/**
	 * <pre>
	 *     - discovered service instance labels do not match input labels
	 *     - no refresh request is sent
	 * </pre>
	 */
	@Test
	void doesNotRefreshWhenNoServiceInstanceMatchesLabels() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		HttpRefreshTrigger refreshTrigger = new HttpRefreshTrigger(reactiveDiscoveryClient, properties,
				WebClient.builder().build());

		when(reactiveDiscoveryClient.getServices()).thenReturn(Flux.just("app-one"));
		when(reactiveDiscoveryClient.getInstances(eq("app-one")))
			.thenReturn(Flux.just(serviceInstance("app-one", "/app-one/actuator",
					Map.of("app", "demo", "tier", "frontend"))));

		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/app-one/actuator/refresh"))
			.willReturn(WireMock.aResponse().withStatus(200)));

		KubernetesSource source = new ConfigMapKubernetesSource(Set.of("my-configmap"),
				Map.of("app", "demo", "tier", "backend"), "my-configmap");

		StepVerifier.create(refreshTrigger.triggerRefresh(source)).verifyComplete();

		WireMock.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/app-one/actuator/refresh")));
	}

	private DefaultKubernetesServiceInstance serviceInstance(String serviceId, String actuatorPath,
			Map<String, String> metadata) {
		return new DefaultKubernetesServiceInstance(serviceId, serviceId, "localhost", wireMockServer.port(),
				metadataWithActuatorPath(metadata, actuatorPath), false, "default", null, Map.of());
	}

	private Map<String, String> metadataWithActuatorPath(Map<String, String> metadata, String actuatorPath) {
		Map<String, String> metadataWithActuatorPath = new HashMap<>(metadata);
		metadataWithActuatorPath.put(ConfigurationWatcherConfigurationProperties.ANNOTATION_KEY,
				"http://:" + wireMockServer.port() + actuatorPath);
		return metadataWithActuatorPath;
	}

}
