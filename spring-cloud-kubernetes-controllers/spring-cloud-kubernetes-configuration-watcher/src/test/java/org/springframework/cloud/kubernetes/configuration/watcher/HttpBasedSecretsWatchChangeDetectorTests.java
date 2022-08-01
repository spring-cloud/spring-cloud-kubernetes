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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
// FIXME: 4.0.0 contract wiremock
@Ignore("waiting for compatible contract wiremock")
@RunWith(MockitoJUnitRunner.class)
public class HttpBasedSecretsWatchChangeDetectorTests {

	// @Rule
	// public WireMockRule wireMockRule = new WireMockRule(0);

	@Mock
	private CoreV1Api coreV1Api;

	@Mock
	private ConfigurationUpdateStrategy updateStrategy;

	@Mock
	private KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator;

	@Mock
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Mock
	private KubernetesInformerReactiveDiscoveryClient reactiveDiscoveryClient;

	private HttpBasedSecretsWatchChangeDetector changeDetector;

	private ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties;

	@Before
	public void setup() {
		V1EndpointAddress fooEndpointAddress = new V1EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");
		V1EndpointPort fooEndpointPort = new V1EndpointPort();
		// fooEndpointPort.setPort(wireMockRule.port());
		List<ServiceInstance> instances = new ArrayList<>();
		KubernetesServiceInstance fooServiceInstance = new KubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), new HashMap<>(), false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty(NAMESPACE_PROPERTY, "default");
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		WebClient webClient = WebClient.builder().build();
		changeDetector = new HttpBasedSecretsWatchChangeDetector(coreV1Api, mockEnvironment, configReloadProperties,
				updateStrategy, secretsPropertySourceLocator, new KubernetesNamespaceProvider(mockEnvironment),
				configurationWatcherConfigurationProperties, threadPoolTaskExecutor, webClient,
				reactiveDiscoveryClient);
	}

	@Test
	public void triggerSecretRefresh() throws InterruptedException {
		V1Secret secret = new V1Secret();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		secret.setMetadata(objectMeta);
		// WireMock.configureFor("localhost", wireMockRule.port());
		// stubFor(post(WireMock.urlEqualTo("/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		// StepVerifier.create(changeDetector.triggerRefresh(secret)).verifyComplete();
		// verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	@Test
	public void triggerSecretRefreshWithPropertiesBasedActuatorPath() throws InterruptedException {
		configurationWatcherConfigurationProperties.setActuatorPath("/my/custom/actuator");
		V1Secret secret = new V1Secret();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		secret.setMetadata(objectMeta);
		// WireMock.configureFor("localhost", wireMockRule.port());
		// stubFor(post(WireMock.urlEqualTo("/my/custom/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		// StepVerifier.create(changeDetector.triggerRefresh(secret)).verifyComplete();
		// verify(postRequestedFor(urlEqualTo("/my/custom/actuator/refresh")));
	}

	@Test
	public void triggerSecretRefreshWithAnnotationActuatorPath() {
		Map<String, String> metadata = new HashMap<>();
		// metadata.put(ANNOTATION_KEY, "http://:" + wireMockRule.port() +
		// "/my/custom/actuator");
		V1EndpointAddress fooEndpointAddress = new V1EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");
		V1EndpointPort fooEndpointPort = new V1EndpointPort();
		// fooEndpointPort.setPort(wireMockRule.port());
		List<ServiceInstance> instances = new ArrayList<>();
		KubernetesServiceInstance fooServiceInstance = new KubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), metadata, false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
		V1Secret secret = new V1Secret();
		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setName("foo");
		secret.setMetadata(objectMeta);
		// stubFor(post(WireMock.urlEqualTo("/my/custom/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		// StepVerifier.create(changeDetector.triggerRefresh(secret)).verifyComplete();
		// verify(postRequestedFor(urlEqualTo("/my/custom/actuator/refresh")));
	}

}
