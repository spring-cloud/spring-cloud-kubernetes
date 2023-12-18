/*
 * Copyright 2013-2023 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.verify;

/**
 * @author wind57
 */
class KubernetesCatalogWatchTests {

	private WireMockServer wireMockServer;

	private static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
			.forClass(HeartbeatEvent.class);

	private static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito
			.mock(ApplicationEventPublisher.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(APPLICATION_EVENT_PUBLISHER);
	}

	@Test
	void testSingleCycleSameAsCurrentState() {

		String body = "[]";

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		stubFor(get("/state")
				.willReturn(aResponse().withStatus(200).withBody(body).withHeader("content-type", "application/json")));

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());

		KubernetesCatalogWatch catalogWatch = new KubernetesCatalogWatch(new RestTemplateBuilder(), properties);
		catalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);

		catalogWatch.catalogServicesWatch();

		Mockito.verifyNoInteractions(APPLICATION_EVENT_PUBLISHER);

	}

	@Test
	@SuppressWarnings("unchecked")
	void testSingleCycleDifferentCurrentState() {

		String body = """
						[
							{
								"endpointName":"endpoint-name",
								"namespace":"namespaceA"
							}
						]
				""";

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		stubFor(get("/state")
				.willReturn(aResponse().withStatus(200).withBody(body).withHeader("content-type", "application/json")));

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());

		KubernetesCatalogWatch catalogWatch = new KubernetesCatalogWatch(new RestTemplateBuilder(), properties);
		catalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);

		catalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		Assertions.assertEquals(event.getSource().getClass(), KubernetesCatalogWatch.class);

		List<EndpointNameAndNamespace> state = (List<EndpointNameAndNamespace>) event.getValue();

		Assertions.assertEquals(state.size(), 1);
		Assertions.assertEquals(state.get(0).namespace(), "namespaceA");

	}

	@Test
	@SuppressWarnings("unchecked")
	void testTwoCyclesDifferentStates() {

		String bodyOne = """
						[
							{
								"endpointName":"endpoint-name",
								"namespace":"namespaceA"
							}
						]
				""";

		// namespace differs
		String bodyTwo = """
						[
							{
								"endpointName":"endpoint-name",
								"namespace":"namespaceB"
							}
						]
				""";

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		stubFor(get("/state").willReturn(
				aResponse().withStatus(200).withBody(bodyOne).withHeader("content-type", "application/json")));

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false,
				wireMockServer.baseUrl());

		KubernetesCatalogWatch catalogWatch = new KubernetesCatalogWatch(new RestTemplateBuilder(), properties);
		catalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);

		catalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
		HeartbeatEvent eventOne = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		Assertions.assertEquals(eventOne.getSource().getClass(), KubernetesCatalogWatch.class);

		List<EndpointNameAndNamespace> stateOne = (List<EndpointNameAndNamespace>) eventOne.getValue();

		Assertions.assertEquals(stateOne.size(), 1);
		Assertions.assertEquals(stateOne.get(0).namespace(), "namespaceA");

		// second call
		stubFor(get("/state").willReturn(
				aResponse().withStatus(200).withBody(bodyTwo).withHeader("content-type", "application/json")));

		catalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER, Mockito.times(2)).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
		HeartbeatEvent eventTwo = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		Assertions.assertEquals(eventTwo.getSource().getClass(), KubernetesCatalogWatch.class);

		List<EndpointNameAndNamespace> stateTwo = (List<EndpointNameAndNamespace>) eventTwo.getValue();

		Assertions.assertEquals(stateTwo.size(), 1);
		Assertions.assertEquals(stateTwo.get(0).namespace(), "namespaceB");

	}

}
