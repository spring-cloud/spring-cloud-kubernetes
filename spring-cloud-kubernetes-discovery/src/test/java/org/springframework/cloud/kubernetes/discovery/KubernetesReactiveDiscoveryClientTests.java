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
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author Ryan Baxter
 */
class KubernetesReactiveDiscoveryClientTests {

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
						"metadata": {"http":"8080"},
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
						"namespace": "namespace1",
						"cluster": null,
						"scheme": "http"
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
					"namespace": "namespace1",
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
		KubernetesDiscoveryClientProperties properties = new KubernetesDiscoveryClientProperties();
		properties.setDiscoveryServerUrl(wireMockServer.baseUrl());
		KubernetesReactiveDiscoveryClient discoveryClient = new KubernetesReactiveDiscoveryClient(WebClient.builder(),
				properties);
		StepVerifier.create(discoveryClient.getServices()).expectNext("test-svc-1", "test-svc-3").verifyComplete();
	}

	@Test
	void getServices() {
		KubernetesDiscoveryClientProperties properties = new KubernetesDiscoveryClientProperties();
		properties.setDiscoveryServerUrl(wireMockServer.baseUrl());
		KubernetesReactiveDiscoveryClient discoveryClient = new KubernetesReactiveDiscoveryClient(WebClient.builder(),
				properties);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("spring", "true");
		metadata.put("http", "8080");
		metadata.put("k8s", "true");
		StepVerifier.create(discoveryClient.getInstances("test-svc-3"))
				.expectNext(new KubernetesServiceInstance("uid2", "test-svc-3", "2.2.2.2", 8080, false,
						URI.create("http://2.2.2.2:8080"), metadata, "http", "namespace1"))
				.verifyComplete();
		StepVerifier.create(discoveryClient.getInstances("test-svc-3")).expectNextCount(0);
	}

}
