/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Some tests that use the fabric8 mock client.
 *
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesCatalogWatchTests {

	private final KubernetesNamespaceProvider namespaceProvider = Mockito.mock(KubernetesNamespaceProvider.class);

	private static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
			.forClass(HeartbeatEvent.class);

	private static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito
			.mock(ApplicationEventPublisher.class);

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setUp() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		Mockito.reset(APPLICATION_EVENT_PUBLISHER);
		mockClient.endpoints().inAnyNamespace().delete();
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We set the namespace to be "namespaceA" and search for labels {color=blue}
	 *     As a result only one pod is taken: podB
	 *
	 * </pre>
	 */
	@Test
	void testEndpointsInSpecificNamespaceWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels("namespaceA", Map.of("color", "blue"));

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We set the namespace to be "namespaceA" and search without labels
	 *     As a result we get three pods:
	 *       - podA in namespaceA
	 *       - podB in namespaceA
	 *       - pocC in namespaceA
	 *
	 * </pre>
	 */
	@Test
	void testEndpointsInSpecificNamespaceWithoutServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels("namespaceA", Map.of());

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We search in all namespaces with labels {color=blue}
	 *     As a result two pods are taken:
	 *       - podB in namespaceA
	 *       - podD in namespaceB
	 *
	 * </pre>
	 */
	@Test
	void testEndpointsInAllNamespacesWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(Map.of("color", "blue"));

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We search in all namespaces without labels
	 *     As a result we get all 5 pods
	 *
	 * </pre>
	 */
	@Test
	void testEndpointsInAllNamespacesWithoutServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(Map.of());

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"), new EndpointNameAndNamespace("podE", "namespaceB"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	private KubernetesCatalogWatch createWatcherInSpecificNamespaceAndLabels(String namespace,
			Map<String, String> labels) {

		when(namespaceProvider.getNamespace()).thenReturn(namespace);

		// all-namespaces = false
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, true, 60, false, "",
				Set.of(), labels, "", null, 0);

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, namespaceProvider);
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	private KubernetesCatalogWatch createWatcherInAllNamespacesAndLabels(Map<String, String> labels) {

		// all-namespaces = true
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, true, 60, false, "",
				Set.of(), labels, "", null, 0);

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, namespaceProvider);
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	private static void createSingleEndpoints(String namespace, Map<String, String> labels, String podName) {

		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withName(podName).withNamespace(namespace).build()).build();

		EndpointSubset endpointSubset = new EndpointSubsetBuilder().withAddresses(List.of(endpointAddress)).build();

		Endpoints endpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(labels).withName("endpoints-" + podName).build())
				.withSubsets(List.of(endpointSubset)).build();
		mockClient.endpoints().inNamespace(namespace).create(endpoints);
	}

}
