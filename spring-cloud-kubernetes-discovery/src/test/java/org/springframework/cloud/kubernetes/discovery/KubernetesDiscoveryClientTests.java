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

package org.springframework.cloud.kubernetes.discovery;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
class KubernetesDiscoveryClientTests {

	private static final String APPS = """
				[{
					"name": "test-svc-1",
					"serviceInstances":
						[{
							"instanceId": "uid1",
							"serviceId": "test-svc-1",
							"host": "2.2.2.2",
							"port": 8080,
							"uri": "http://2.2.2.2:8080",
							"secure": false,
							"metadata":{"http": "8080"},
							"namespace": "namespace1",
							"cluster": null,
							"scheme": "http"
						}]
					},
					{
						"name": "test-svc-3",
						"serviceInstances":
							[{
								"instanceId": "uid2",
								"serviceId": "test-svc-3",
								"host": "2.2.2.2",
								"port": 8080,
								"uri": "http://2.2.2.2:8080",
								"secure": false,
								"metadata": {"spring": "true", "http": "8080", "k8s": "true"},
								"namespace": "namespace2",
								"cluster":null,
								"scheme":"http"
							}]
						}]
			""";

	private static final String APPS_NAME = """
				[{
					"instanceId": "uid2",
					"serviceId": "test-svc-3",
					"host": "2.2.2.2",
					"port": 8080,
					"uri": "http://2.2.2.2:8080",
					"secure": false,
					"metadata": {"spring": "true", "http": "8080", "k8s": "true"},
					"namespace": "namespace2",
					"cluster": null,
					"scheme": "http"
				}]
			""";

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		stubFor(get("/apps")
				.willReturn(aResponse().withStatus(200).withBody(APPS).withHeader("content-type", "application/json")));
		stubFor(get("/apps/test-svc-3").willReturn(
				aResponse().withStatus(200).withBody(APPS_NAME).withHeader("content-type", "application/json")));
		stubFor(get("/apps/does-not-exist")
				.willReturn(aResponse().withStatus(200).withBody("").withHeader("content-type", "application/json")));
	}

	@Test
	void getInstances() {
		RestTemplate rest = new RestTemplateBuilder().build();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(rest, properties);
		assertThat(discoveryClient.getServices()).contains("test-svc-1", "test-svc-3");
	}

	@Test
	void getServices() {
		RestTemplate rest = new RestTemplateBuilder().build();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(rest, properties);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("spring", "true");
		metadata.put("http", "8080");
		metadata.put("k8s", "true");
		assertThat(discoveryClient.getInstances("test-svc-3"))
				.contains(new KubernetesServiceInstance("uid2", "test-svc-3", "2.2.2.2", 8080, false,
						URI.create("http://2.2.2.2:8080"), metadata, "http", "namespace2"));
		assertThat(discoveryClient.getInstances("does-not-exist")).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("servicesFilteredByNamespacesSource")
	void getServicesFilteredByNamespaces(Set<String> namespaces, List<String> expectedServices) {
		RestTemplate rest = new RestTemplateBuilder().build();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, namespaces, true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(rest, properties);
		assertThat(discoveryClient.getServices()).containsExactlyInAnyOrderElementsOf(expectedServices);
	}

	@ParameterizedTest
	@MethodSource("instancesFilteredByNamespacesSource")
	void getInstancesFilteredByNamespaces(Set<String> namespaces, String serviceId, List<String> expectedInstances) {
		RestTemplate rest = new RestTemplateBuilder().build();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, namespaces, true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(rest, properties);
		assertThat(discoveryClient.getInstances(serviceId)).map(ServiceInstance::getInstanceId)
				.containsExactlyInAnyOrderElementsOf(expectedInstances);
	}

	private static Stream<Arguments> servicesFilteredByNamespacesSource() {
		return Stream.of(Arguments.of(Set.of(), List.of("test-svc-1", "test-svc-3")),
				Arguments.of(Set.of("namespace1", "namespace2"), List.of("test-svc-1", "test-svc-3")),
				Arguments.of(Set.of("namespace1"), List.of("test-svc-1")),
				Arguments.of(Set.of("namespace2", "does-not-exist"), List.of("test-svc-3")));
	}

	private static Stream<Arguments> instancesFilteredByNamespacesSource() {
		return Stream.of(Arguments.of(Set.of(), "test-svc-3", List.of("uid2")),
				Arguments.of(Set.of("namespace1"), "test-svc-3", List.of()),
				Arguments.of(Set.of("namespace2"), "test-svc-3", List.of("uid2")));
	}

}
