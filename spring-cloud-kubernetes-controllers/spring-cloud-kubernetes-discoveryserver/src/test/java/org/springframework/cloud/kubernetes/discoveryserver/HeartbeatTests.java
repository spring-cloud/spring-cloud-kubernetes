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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=true",
		/* disable kubernetes from liveness and readiness */
		"management.health.livenessstate.enabled=true",
		"management.endpoint.health.group.liveness.include=livenessState",
		"management.health.readinessstate.enabled=true",
		"management.endpoint.health.group.readiness.include=readinessState" })
@AutoConfigureWebTestClient
class HeartbeatTests {

	@Autowired
	private WebTestClient client;

	@Autowired
	private ApplicationEventPublisher publisher;

	@MockitoBean
	KubernetesClientInformerReactiveDiscoveryClient informerReactiveDiscoveryClient;

	@Test
	void testHeartbeat() {
		client.get().uri("/state").exchange().expectStatus().is2xxSuccessful().expectBody().json("[]");

		publisher.publishEvent(
				new HeartbeatEvent("test", List.of(new EndpointNameAndNamespace("endpoint-name", "namespaceA"))));
		client.get().uri("/state").exchange().expectStatus().is2xxSuccessful().expectBody().json("""
						[
							{
								"endpointName":"endpoint-name",
								"namespace":"namespaceA"
							}
						]
				""");
	}

}
