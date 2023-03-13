/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@ExtendWith(MockitoExtension.class)
class HttpBasedConfigMapWatchChangeDetectorTests {

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(
			WireMockConfiguration.options().dynamicPort());

	@Mock
	private CoreV1Api coreV1Api;

	@Mock
	private KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator;

	@Mock
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Mock
	private KubernetesInformerReactiveDiscoveryClient reactiveDiscoveryClient;

	private HttpBasedConfigMapWatchChangeDetector changeDetector;

	private ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties;

	@BeforeAll
	static void beforeAll() {
		WIRE_MOCK_SERVER.start();
		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::createApiClientForInformerClient)
				.thenReturn(new ClientBuilder().setBasePath(WIRE_MOCK_SERVER.baseUrl()).build());
		clientUtilsMock
				.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn("default");
	}

	@AfterAll
	static void teardown() {
		clientUtilsMock.close();
	}

	@BeforeEach
	void setup() {

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty(NAMESPACE_PROPERTY, "default");
		configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		WebClient webClient = WebClient.builder().build();

		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("refresh", () -> {

		});

		changeDetector = new HttpBasedConfigMapWatchChangeDetector(coreV1Api, mockEnvironment,
				ConfigReloadProperties.DEFAULT, strategy, configMapPropertySourceLocator,
				new KubernetesNamespaceProvider(mockEnvironment), configurationWatcherConfigurationProperties,
				threadPoolTaskExecutor, new HttpRefreshTrigger(reactiveDiscoveryClient,
						configurationWatcherConfigurationProperties, webClient));
	}

	@Test
	void triggerConfigMapRefresh() {
		stubReactiveCall();
		V1ConfigMap configMap = new V1ConfigMap();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		WireMock.configureFor("localhost", WIRE_MOCK_SERVER.port());
		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/actuator/refresh"))
				.willReturn(WireMock.aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap, configMap.getMetadata().getName()))
				.verifyComplete();
		WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));
	}

	@Test
	void triggerConfigMapRefreshWithPropertiesBasedActuatorPath() {
		stubReactiveCall();
		configurationWatcherConfigurationProperties.setActuatorPath("/my/custom/actuator");
		V1ConfigMap configMap = new V1ConfigMap();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		WireMock.configureFor("localhost", WIRE_MOCK_SERVER.port());
		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/my/custom/actuator/refresh"))
				.willReturn(WireMock.aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap, configMap.getMetadata().getName()))
				.verifyComplete();
		WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/my/custom/actuator/refresh")));
	}

	@Test
	void triggerConfigMapRefreshWithAnnotationActuatorPath() {
		int port = WIRE_MOCK_SERVER.port();
		WireMock.configureFor("localhost", port);
		Map<String, String> metadata = new HashMap<>();
		metadata.put(ConfigurationWatcherConfigurationProperties.ANNOTATION_KEY,
				"http://:" + port + "/my/custom/actuator");
		V1EndpointAddress fooEndpointAddress = new V1EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");
		CoreV1EndpointPort fooEndpointPort = new CoreV1EndpointPort();
		fooEndpointPort.setPort(port);
		List<ServiceInstance> instances = new ArrayList<>();
		DefaultKubernetesServiceInstance fooServiceInstance = new DefaultKubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), metadata, false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
		V1ConfigMap configMap = new V1ConfigMap();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/my/custom/actuator/refresh"))
				.willReturn(WireMock.aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap, configMap.getMetadata().getName()))
				.verifyComplete();
		WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/my/custom/actuator/refresh")));
	}

	private void stubReactiveCall() {

		V1EndpointAddress fooEndpointAddress = new V1EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");

		CoreV1EndpointPort fooEndpointPort = new CoreV1EndpointPort();
		fooEndpointPort.setPort(WIRE_MOCK_SERVER.port());

		List<ServiceInstance> instances = new ArrayList<>();
		DefaultKubernetesServiceInstance fooServiceInstance = new DefaultKubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), new HashMap<>(), false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
	}

}
