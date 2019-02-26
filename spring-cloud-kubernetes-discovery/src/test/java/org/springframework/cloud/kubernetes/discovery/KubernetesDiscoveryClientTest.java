/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import java.util.HashMap;
import java.util.List;

import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesDiscoveryClientTest {

	@ClassRule
	public static KubernetesServer mockServer = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Before
	public void setup() {
		mockClient = mockServer.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddress() {
		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/endpoint")
				.andReturn(200,
						new EndpointsBuilder().withNewMetadata().withName("endpoint")
								.endMetadata().addNewSubset().addNewAddress()
								.withIp("ip1").endAddress().addNewPort("http", 80, "TCP")
								.endSubset().build())
				.once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint")
				.andReturn(200, new ServiceBuilder().withNewMetadata()
						.withName("endpoint").withLabels(new HashMap<String, String>() {
							{
								put("l", "v");
							}
						}).endMetadata().build())
				.once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				properties, KubernetesClient::services,
				new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1)
				.filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1);
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsMultipleAddresses() {
		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/endpoint")
				.andReturn(200, new EndpointsBuilder().withNewMetadata()
						.withName("endpoint").endMetadata().addNewSubset().addNewAddress()
						.withIp("ip1").endAddress().addNewAddress().withIp("ip2")
						.endAddress().addNewPort("https", 443, "TCP").endSubset().build())
				.once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint")
				.andReturn(200, new ServiceBuilder().withNewMetadata()
						.withName("endpoint").withLabels(new HashMap<String, String>() {
							{
								put("l", "v");
							}
						}).endMetadata().build())
				.once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				properties, KubernetesClient::services,
				new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2).filteredOn(ServiceInstance::isSecure)
				.extracting(ServiceInstance::getHost).containsOnly("ip1", "ip2");
	}

	@Test
	public void getServicesShouldReturnAllServicesWhenNoLabelsAreAppliedToTheClient() {
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata()
						.withName("s1").withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().addNewItem().withNewMetadata()
						.withName("s2").withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
								put("label2", "value2");
							}
						}).endMetadata().endItem().addNewItem().withNewMetadata()
						.withName("s3").endMetadata().endItem().build())
				.once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				properties, KubernetesClient::services,
				new DefaultIsServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2", "s3");
	}

	@Test
	public void getServicesShouldReturnOnlyMatchingServicesWhenLabelsAreAppliedToTheClient() {
		mockServer.expect().get()
				.withPath("/api/v1/namespaces/test/services?labelSelector=label%3Dvalue")
				.andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata()
						.withName("s1").withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
							}
						}).endMetadata().endItem().addNewItem().withNewMetadata()
						.withName("s2").withLabels(new HashMap<String, String>() {
							{
								put("label", "value");
								put("label2", "value2");
							}
						}).endMetadata().endItem().build())
				.once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				properties,
				client -> client.services().withLabels(new HashMap<String, String>() {
					{
						put("label", "value");
					}
				}), new DefaultIsServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2");
	}

}
