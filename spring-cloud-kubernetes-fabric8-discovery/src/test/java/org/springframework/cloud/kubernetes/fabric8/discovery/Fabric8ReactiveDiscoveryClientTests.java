/*
 * Copyright 2019-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

/**
 * @author Tim Ysewyn
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ReactiveDiscoveryClientTests extends Fabric8DiscoveryClientBase {

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
		kubernetesClient.services().inAnyNamespace().delete();
		kubernetesClient.endpoints().inAnyNamespace().delete();
	}

	@Test
	void verifyDefaults() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
			false, null, Set.of(), Map.of("label1", "one"), null, metadata, 0, true, false, null);

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of(), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);
		assertThat(client.description()).isEqualTo("Fabric8 Reactive Discovery Client");
		assertThat(client.getOrder()).isEqualTo(ReactiveDiscoveryClient.DEFAULT_ORDER);
	}

	@Test
	void shouldReturnFluxOfServices() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Service s1 = service("test", "s1", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s1).create();

		Service s2 = service("test", "s2", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s2).create();

		Service s3 = service("test", "s3", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s3).create();

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of("test"), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);

		Flux<String> services = client.getServices();
		StepVerifier.create(services)
			.recordWith(ArrayList::new)
			.expectNextCount(3)
			.consumeRecordedWith(list ->
				assertThat(list).containsExactlyInAnyOrder("s1", "s2", "s3"))
			.expectComplete()
			.verify();
	}

	@Test
	void shouldReturnEmptyFluxOfServicesWhenNoInstancesFound() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of("test"), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);

		Flux<String> services = client.getServices();
		StepVerifier.create(services).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnEmptyFluxForNonExistingService() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of("test"), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);

		Flux<ServiceInstance> instances = client.getInstances("nonexistent-service");
		StepVerifier.create(instances).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnEmptyFluxWhenServiceHasNoSubsets() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Service s1 = service("test", "s1", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s1).create();

		Endpoints e1 = new EndpointsBuilder().withNewMetadata().withName("s1").endMetadata().build();
		kubernetesClient.endpoints().inNamespace("test").resource(e1).create();

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of("test"), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);

		Flux<ServiceInstance> instances = client.getInstances("s1");
		StepVerifier.create(instances).expectNextCount(0).expectComplete().verify();
	}

	@Test
	void shouldReturnFlux() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Service s1 = service("test", "s1", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s1).create();

		Endpoints e1 = endpoints("test", "s1", Map.of(), Map.of());
		kubernetesClient.endpoints().inNamespace("test").resource(e1).create();

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of("test"), kubernetesClient);
		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);

		Flux<ServiceInstance> instances = client.getInstances("s1");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

	@Test
	void shouldReturnFluxOfServicesAcrossAllNamespaces() {

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("test"), true, 60,
			false, null, Set.of(), Map.of(), null, metadata, 0, true, false, null);

		Service s1 = service("test", "s1", Map.of(), Map.of(), Map.of());
		kubernetesClient.services().inNamespace("test").resource(s1).create();

		Endpoints e1 = endpoints("test", "s1", Map.of(), Map.of());
		kubernetesClient.endpoints().inNamespace("test").resource(e1).create();

		Fabric8DiscoveryClient fabric8KubernetesDiscoveryClient = fabric8DiscoveryClient(properties, List.of(""), kubernetesClient);

		ReactiveDiscoveryClient client = new Fabric8ReactiveDiscoveryClient(fabric8KubernetesDiscoveryClient);
		Flux<ServiceInstance> instances = client.getInstances("s1");
		StepVerifier.create(instances).expectNextCount(1).expectComplete().verify();
	}

}
