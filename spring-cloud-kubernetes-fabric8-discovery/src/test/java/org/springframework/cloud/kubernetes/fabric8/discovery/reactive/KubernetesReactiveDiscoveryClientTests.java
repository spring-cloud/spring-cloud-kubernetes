/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

/**
 * @author Tim Ysewyn
 */
@EnableKubernetesMockClient(crud = true, https = false)
class KubernetesReactiveDiscoveryClientTests {

	private static KubernetesMockServer kubernetesServer;

	private static KubernetesClient kubernetesClient;

	@BeforeEach
	void beforeEach() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				kubernetesClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		kubernetesServer.clearExpectations();
	}

	@Test
	void verifyDefaults() {
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		assertThat(client.description()).isEqualTo("Fabric8 Kubernetes Reactive Discovery Client");
		assertThat(client.getOrder()).isEqualTo(ReactiveDiscoveryClient.DEFAULT_ORDER);
	}

	@Test
	void shouldReturnFluxOfServices() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("s1")
						.withLabels(Map.of("label", "value")).endMetadata().endItem().addNewItem().withNewMetadata()
						.withName("s2").withLabels(Map.of("label", "value", "label2", "value2")).endMetadata().endItem()
						.addNewItem().withNewMetadata().withName("s3").endMetadata().endItem().build())
				.once();
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<String> services = client.getServices();
		StepVerifier.create(services).expectNext("s1", "s2", "s3").expectComplete().verify();
	}

	@Test
	void shouldReturnEmptyFluxOfServicesWhenNoInstancesFound() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().build()).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<String> services = client.getServices();
		StepVerifier.create(services).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnEmptyFluxForNonExistingService() {
		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dnonexistent-service")
				.andReturn(200, new EndpointsBuilder().build()).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("nonexistent-service");
		StepVerifier.create(instances).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnEmptyFluxWhenServiceHasNoSubsets() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
						.withLabels(Map.of("label", "value")).endMetadata().endItem().build())
				.once();

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, new EndpointsBuilder().build()).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnFlux() {
		ServiceList services = new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
				.withNamespace("test").withLabels(Map.of("label", "value")).endMetadata()
				.withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).endItem().build();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("existing-service").withNamespace("test")
				.withLabels(Map.of("label", "value")).endMetadata().addNewSubset().addNewAddress().withIp("ip1")
				.withNewTargetRef().withUid("uid1").endTargetRef().endAddress()
				.addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpoints).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service")
				.andReturn(200, services.getItems().get(0)).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services").andReturn(200, services).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	void shouldReturnFluxWithPrefixedMetadata() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200,
						new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
								.withLabels(Map.of("label", "value")).endMetadata()
								.withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).endItem().build())
				.once();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("existing-service").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("uid1")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpoints).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service").andReturn(200,
				new ServiceBuilder().withNewMetadata().withName("existing-service").withLabels(Map.of("label", "value"))
						.endMetadata().withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).build())
				.once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	void shouldReturnFluxWhenServiceHasMultiplePortsAndPrimaryPortNameIsSet() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200,
						new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
								.withLabels(Map.of("label", "value")).endMetadata()
								.withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).endItem().build())
				.once();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("existing-service").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("uid1")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP")
				.addNewPort("https", "https_tcp", 443, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpoints).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service").andReturn(200,
				new ServiceBuilder().withNewMetadata().withName("existing-service").withLabels(Map.of("label", "value"))
						.endMetadata().withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).build())
				.once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	void shouldReturnFluxOfServicesAcrossAllNamespaces() {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200,
						new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
								.withLabels(Map.of("label", "value")).endMetadata()
								.withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).endItem().build())
				.once();

		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName("existing-service")
				.withNamespace("test").endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("uid1").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP")
				.addNewPort("https", "https_tcp", 443, "TCP").endSubset().build();

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(singletonList(endpoints));

		kubernetesServer.expect().get().withPath("/api/v1/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpointsList).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service").andReturn(200,
				new ServiceBuilder().withNewMetadata().withName("existing-service").withLabels(Map.of("label", "value"))
						.endMetadata().withSpec(new ServiceSpecBuilder().withType("ExternalName").build()).build())
				.once();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), "https_tcp", Metadata.DEFAULT, 0, true);
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient, properties,
				KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

}
