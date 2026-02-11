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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = false, https = false)
abstract class Fabric8DiscoveryClientServiceLabelsFiltering {

	private static KubernetesMockServer kubernetesMockServer;

	private static KubernetesClient kubernetesClient;

	private static final Fabric8InformerAutoConfiguration CONFIGURATION = new Fabric8InformerAutoConfiguration();

	@BeforeAll
	static void beforeAll() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
	}

	@BeforeEach
	void beforeEach() {
		mockAllPossibleCalls();
	}

	@AfterEach
	void afterEach() {
		kubernetesMockServer.clearExpectations();
	}

	private static final Service NAMESPACE_A__SERVICE_X = service("serviceX", "namespaceA",
			Map.of("color", "red", "namespace", "a"));

	private static final Service NAMESPACE_A__SERVICE_XX = service("serviceXX", "namespaceA",
			Map.of("color", "green", "namespace", "a"));

	private static final Endpoints NAMESPACE_A__ENDPOINTS_X = endpoints("serviceX", "namespaceA",
			Map.of("color", "red", "namespace", "a"));

	private static final Endpoints NAMESPACE_A__ENDPOINTS_XX = endpoints("serviceXX", "namespaceA",
			Map.of("color", "green", "namespace", "a"));

	private static final Service NAMESPACE_B__SERVICE_X = service("serviceX", "namespaceB",
			Map.of("color", "red", "namespace", "b"));

	private static final Service NAMESPACE_B__SERVICE_XX = service("serviceXX", "namespaceB",
			Map.of("color", "green", "namespace", "b"));

	private static final Endpoints NAMESPACE_B__ENDPOINTS_X = endpoints("serviceX", "namespaceB",
			Map.of("color", "red", "namespace", "b"));

	private static final Endpoints NAMESPACE_B__ENDPOINTS_XX = endpoints("serviceXX", "namespaceB",
			Map.of("color", "green", "namespace", "b"));

	void mockAllPossibleCalls() {
		mockServicesCallNamespaceAServiceX();
		mockServicesCallNamespaceAServiceXX();

		mockEndpointsCallNamespaceAServiceX();
		mockEndpointsCallNamespaceAServiceXX();

		mockServicesCallNamespaceBServiceX();
		mockServicesCallNamespaceBServiceXX();

		mockEndpointsCallNamespaceBServiceX();
		mockEndpointsCallNamespaceBServiceXX();

		mockServicesCallNamespaceANoLabels();
		mockEndpointsCallNamespaceANoLabels();

		mockServicesCallNamespaceBNoLabels();
		mockEndpointsCallNamespaceBNoLabels();

		mockServicesCallAllNamespacesRedLabel();
		mockEndpointsCallAllNamespacesRedLabel();

		mockServicesCallAllNamespacesGreenLabel();
		mockEndpointsCallAllNamespacesGreenLabel();

		mockServicesCallAllNamespacesNoLabels();
		mockEndpointsCallAllNamespacesNoLabels();
	}

	Fabric8DiscoveryClient createAndStartListers(List<String> namespaces, KubernetesDiscoveryProperties properties) {

		List<SharedIndexInformer<Service>> serviceSharedIndexInformers = CONFIGURATION
			.serviceSharedIndexInformers(namespaces, kubernetesClient, properties);

		List<SharedIndexInformer<Endpoints>> endpointsSharedIndexInformers = CONFIGURATION
			.endpointsSharedIndexInformers(namespaces, kubernetesClient, properties);

		List<Lister<Service>> serviceListers = CONFIGURATION.serviceListers(namespaces, serviceSharedIndexInformers);
		List<Lister<Endpoints>> endpointsListers = CONFIGURATION.endpointsListers(namespaces,
				endpointsSharedIndexInformers);

		startInformers(serviceSharedIndexInformers, endpointsSharedIndexInformers);

		return new Fabric8DiscoveryClient(kubernetesClient, serviceListers, endpointsListers,
				serviceSharedIndexInformers, endpointsSharedIndexInformers, properties, x -> true);
	}

	/**
	 * <pre>
	 *  lots of mock and helpers follow
	 *  ***********************************************************************************************************
	 *  ***********************************************************************************************************
	 * </pre>
	 */
	private static void mockServicesCallNamespaceAServiceX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/services?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new ServiceListBuilder().withItems(NAMESPACE_A__SERVICE_X)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/services?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallNamespaceAServiceXX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/services?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new ServiceListBuilder().withItems(NAMESPACE_A__SERVICE_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/services?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceAServiceX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/endpoints?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new EndpointsListBuilder().withItems(NAMESPACE_A__ENDPOINTS_X)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceAServiceXX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/endpoints?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new EndpointsListBuilder().withItems(NAMESPACE_A__ENDPOINTS_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallNamespaceBServiceX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/services?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new ServiceListBuilder().withItems(NAMESPACE_B__SERVICE_X)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/services?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallNamespaceBServiceXX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/services?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new ServiceListBuilder().withItems(NAMESPACE_B__SERVICE_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/services?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceBServiceX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/endpoints?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new EndpointsListBuilder().withItems(NAMESPACE_B__ENDPOINTS_X)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceBServiceXX() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/endpoints?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new EndpointsListBuilder().withItems(NAMESPACE_B__ENDPOINTS_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallNamespaceANoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/services?resourceVersion=0")
			.andReturn(200,
					Serialization
						.asJson(new ServiceListBuilder().withItems(NAMESPACE_A__SERVICE_X, NAMESPACE_A__SERVICE_XX)
							.withNewMetadata()
							.withResourceVersion("1")
							.endMetadata()
							.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceANoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceA/endpoints?resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(
							new EndpointsListBuilder().withItems(NAMESPACE_A__ENDPOINTS_X, NAMESPACE_A__ENDPOINTS_XX)
								.withNewMetadata()
								.withResourceVersion("1")
								.endMetadata()
								.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceA/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallNamespaceBNoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/services?resourceVersion=0")
			.andReturn(200,
					Serialization
						.asJson(new ServiceListBuilder().withItems(NAMESPACE_B__SERVICE_X, NAMESPACE_B__SERVICE_XX)
							.withNewMetadata()
							.withResourceVersion("1")
							.endMetadata()
							.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallNamespaceBNoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/namespaces/namespaceB/endpoints?resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(
							new EndpointsListBuilder().withItems(NAMESPACE_B__ENDPOINTS_X, NAMESPACE_B__ENDPOINTS_XX)
								.withNewMetadata()
								.withResourceVersion("1")
								.endMetadata()
								.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/namespaces/namespaceB/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallAllNamespacesRedLabel() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization
						.asJson(new ServiceListBuilder().withItems(NAMESPACE_A__SERVICE_X, NAMESPACE_B__SERVICE_X)
							.withNewMetadata()
							.withResourceVersion("1")
							.endMetadata()
							.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/services?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallAllNamespacesRedLabel() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?labelSelector=color%3Dred&resourceVersion=0")
			.andReturn(200,
					Serialization
						.asJson(new EndpointsListBuilder().withItems(NAMESPACE_A__ENDPOINTS_X, NAMESPACE_B__ENDPOINTS_X)
							.withNewMetadata()
							.withResourceVersion("1")
							.endMetadata()
							.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dred&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallAllNamespacesGreenLabel() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization
						.asJson(new ServiceListBuilder().withItems(NAMESPACE_A__SERVICE_XX, NAMESPACE_B__SERVICE_XX)
							.withNewMetadata()
							.withResourceVersion("1")
							.endMetadata()
							.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/services?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallAllNamespacesGreenLabel() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?labelSelector=color%3Dgreen&resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(
							new EndpointsListBuilder().withItems(NAMESPACE_A__ENDPOINTS_XX, NAMESPACE_B__ENDPOINTS_XX)
								.withNewMetadata()
								.withResourceVersion("1")
								.endMetadata()
								.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath(
					"/api/v1/endpoints?allowWatchBookmarks=true&labelSelector=color%3Dgreen&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockServicesCallAllNamespacesNoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new ServiceListBuilder()
						.withItems(NAMESPACE_A__SERVICE_X, NAMESPACE_A__SERVICE_XX, NAMESPACE_B__SERVICE_X,
								NAMESPACE_B__SERVICE_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static void mockEndpointsCallAllNamespacesNoLabels() {
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?resourceVersion=0")
			.andReturn(200,
					Serialization.asJson(new EndpointsListBuilder()
						.withItems(NAMESPACE_A__ENDPOINTS_X, NAMESPACE_A__ENDPOINTS_XX, NAMESPACE_B__ENDPOINTS_X,
								NAMESPACE_B__ENDPOINTS_XX)
						.withNewMetadata()
						.withResourceVersion("1")
						.endMetadata()
						.build()))
			.always();

		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/endpoints?allowWatchBookmarks=true&resourceVersion=1&timeoutSeconds=600&watch=true")
			.andReturn(200, "")
			.always();
	}

	private static Service service(String name, String namespace, Map<String, String> labels) {
		return new ServiceBuilder()
			.withMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).withLabels(labels).build())
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.build();
	}

	private static Endpoints endpoints(String name, String namespace, Map<String, String> labels) {
		return new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).withLabels(labels).build())
			.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build())
				.withAddresses(new EndpointAddressBuilder().withIp("2.2.2.2").build())
				.build())
			.build();
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet()
			.stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining(","));
	}

	private void startInformers(List<SharedIndexInformer<Service>> serviceSharedIndexInformers,
			List<SharedIndexInformer<Endpoints>> endpointsSharedIndexInformers) {

		Awaitilities.awaitUntil(10, 100,
				() -> serviceSharedIndexInformers.stream()
					.map(SharedIndexInformer::hasSynced)
					.reduce(Boolean::logicalAnd)
					.orElse(false));

		Awaitilities.awaitUntil(10, 100,
				() -> endpointsSharedIndexInformers.stream()
					.map(SharedIndexInformer::hasSynced)
					.reduce(Boolean::logicalAnd)
					.orElse(false));
	}

}
