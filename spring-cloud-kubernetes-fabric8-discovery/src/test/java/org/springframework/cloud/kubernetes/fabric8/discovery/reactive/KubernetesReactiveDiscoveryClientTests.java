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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.discovery.support.KubernetesExtension;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

/**
 * @author Tim Ysewyn
 */
@ExtendWith(KubernetesExtension.class)
class KubernetesReactiveDiscoveryClientTests {

	@BeforeEach
	public void setup(@KubernetesExtension.Client KubernetesClient kubernetesClient) {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				kubernetesClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@Test
	public void verifyDefaults(@KubernetesExtension.Client KubernetesClient kubernetesClient) {
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		assertThat(client.description()).isEqualTo("Kubernetes Reactive Discovery Client");
		assertThat(client.getOrder()).isEqualTo(ReactiveDiscoveryClient.DEFAULT_ORDER);
	}

	@Test
	public void shouldReturnFluxOfServices(@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("s1")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().addNewItem().withNewMetadata().withName("s2")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
								put("label2", "value2");
							}
						}).endMetadata().endItem().addNewItem().withNewMetadata().withName("s3").endMetadata().endItem()
						.build())
				.once();
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<String> services = client.getServices();
		StepVerifier.create(services).expectNext("s1", "s2", "s3").expectComplete().verify();
	}

	@Test
	public void shouldReturnEmptyFluxOfServicesWhenNoInstancesFound(
			@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().build()).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<String> services = client.getServices();
		StepVerifier.create(services).expectNextCount(0).expectComplete().verify();
	}

	@Test
	public void shouldReturnEmptyFluxForNonExistingService(
			@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dnonexistent-service")
				.andReturn(200, new EndpointsBuilder().build()).once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("nonexistent-service");
		StepVerifier.create(instances).expectNextCount(0).expectComplete().verify();
	}

	@Test
	public void shouldReturnEmptyFluxWhenServiceHasNoSubsets(
			@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().build())
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
	public void shouldReturnFlux(@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		ServiceList services = new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
				.withNamespace("test").withLabels(new HashMap<String, String>() {
					{
						put("label", "value");
					}
				}).endMetadata().endItem().build();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("existing-service").withNamespace("test")
				.withLabels(new HashMap<String, String>() {
					{
						put("label", "value");
					}
				}).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("uid1")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpoints).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service")
				.andReturn(200, services.getItems().get(0)).once();

		Metadata metadata = new Metadata(false, null, false, null, true, "port.");
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	public void shouldReturnFluxWithPrefixedMetadata(@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().build())
				.once();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("uid1")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		kubernetesServer.expect().get()
				.withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpoints).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service")
				.andReturn(200, new ServiceBuilder().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().build())
				.once();

		Metadata metadata = new Metadata(true, "label.", true, "annotation.", true, "port.");
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	public void shouldReturnFluxWhenServiceHasMultiplePortsAndPrimaryPortNameIsSet(
			@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().build())
				.once();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
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

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service")
				.andReturn(200, new ServiceBuilder().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().build())
				.once();

		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	public void shouldReturnFluxOfServicesAcrossAllNamespaces(
			@KubernetesExtension.Client KubernetesClient kubernetesClient,
			@KubernetesExtension.Server KubernetesServer kubernetesServer) {
		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().build())
				.once();

		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("uid1")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP")
				.addNewPort("https", "https_tcp", 443, "TCP").endSubset().build();

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(singletonList(endpoints));

		kubernetesServer.expect().get().withPath("/api/v1/endpoints?fieldSelector=metadata.name%3Dexisting-service")
				.andReturn(200, endpointsList).once();

		kubernetesServer.expect().get().withPath("/api/v1/namespaces/test/services/existing-service")
				.andReturn(200, new ServiceBuilder().withNewMetadata().withName("existing-service")
						.withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().build())
				.once();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, true, 60, false, null,
				Set.of(), Map.of(), "https_tcp", Metadata.DEFAULT, 0);
		ReactiveDiscoveryClient client = new KubernetesReactiveDiscoveryClient(kubernetesClient, properties,
				KubernetesClient::services);
		Flux<ServiceInstance> instances = client.getInstances("existing-service");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

}
