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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.discovery.reactive.KubernetesReactiveDiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.configuration.watcher.HttpBasedConfigMapWatchChangeDetector.ANNOTATION_KEY;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
@RunWith(MockitoJUnitRunner.class)
public class HttpBasedConfigMapWatchChangeDetectorTests {

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(0);

	@Mock
	private KubernetesClient client;

	@Mock
	private ConfigurationUpdateStrategy updateStrategy;

	@Mock
	private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	@Mock
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Mock
	private KubernetesReactiveDiscoveryClient reactiveDiscoveryClient;

	private HttpBasedConfigMapWatchChangeDetector changeDetector;

	private ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties;

	@Before
	public void setup() {
		EndpointAddress fooEndpointAddress = new EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");
		EndpointPort fooEndpointPort = new EndpointPort();
		fooEndpointPort.setPort(wireMockRule.port());
		List<ServiceInstance> instances = new ArrayList<>();
		KubernetesServiceInstance fooServiceInstance = new KubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), new HashMap<>(), false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
		MockEnvironment mockEnvironment = new MockEnvironment();
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		configurationWatcherConfigurationProperties = new ConfigurationWatcherConfigurationProperties();
		WebClient webClient = WebClient.builder().build();
		changeDetector = new HttpBasedConfigMapWatchChangeDetector(mockEnvironment, configReloadProperties, client,
				updateStrategy, configMapPropertySourceLocator, configurationWatcherConfigurationProperties,
				threadPoolTaskExecutor, webClient, reactiveDiscoveryClient);
	}

	@Test
	public void triggerConfigMapRefresh() {
		ConfigMap configMap = new ConfigMap();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		WireMock.configureFor("localhost", wireMockRule.port());
		stubFor(post(WireMock.urlEqualTo("/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap)).verifyComplete();
		verify(postRequestedFor(urlEqualTo("/actuator/refresh")));
	}

	@Test
	public void triggerConfigMapRefreshWithPropertiesBasedActuatorPath() throws InterruptedException {
		configurationWatcherConfigurationProperties.setActuatorPath("/my/custom/actuator");
		ConfigMap configMap = new ConfigMap();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		WireMock.configureFor("localhost", wireMockRule.port());
		stubFor(post(WireMock.urlEqualTo("/my/custom/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap)).verifyComplete();
		verify(postRequestedFor(urlEqualTo("/my/custom/actuator/refresh")));
	}

	@Test
	public void triggerConfigMapRefreshWithAnnotationActuatorPath() {
		Map<String, String> metadata = new HashMap<>();
		metadata.put(ANNOTATION_KEY, "http://:" + wireMockRule.port() + "/my/custom/actuator");
		EndpointAddress fooEndpointAddress = new EndpointAddress();
		fooEndpointAddress.setIp("127.0.0.1");
		fooEndpointAddress.setHostname("localhost");
		EndpointPort fooEndpointPort = new EndpointPort();
		fooEndpointPort.setPort(wireMockRule.port());
		List<ServiceInstance> instances = new ArrayList<>();
		KubernetesServiceInstance fooServiceInstance = new KubernetesServiceInstance("foo", "foo",
				fooEndpointAddress.getIp(), fooEndpointPort.getPort(), metadata, false);
		instances.add(fooServiceInstance);
		when(reactiveDiscoveryClient.getInstances(eq("foo"))).thenReturn(Flux.fromIterable(instances));
		ConfigMap configMap = new ConfigMap();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("foo");
		configMap.setMetadata(objectMeta);
		stubFor(post(WireMock.urlEqualTo("/my/custom/actuator/refresh")).willReturn(aResponse().withStatus(200)));
		StepVerifier.create(changeDetector.triggerRefresh(configMap)).verifyComplete();
		verify(postRequestedFor(urlEqualTo("/my/custom/actuator/refresh")));
	}

}
