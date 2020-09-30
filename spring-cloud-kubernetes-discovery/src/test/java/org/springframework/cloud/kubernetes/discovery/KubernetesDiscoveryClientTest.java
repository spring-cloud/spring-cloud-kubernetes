/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
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
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddress() {
		Map<String, String> labels = new HashMap();
		labels.put("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("10").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		mockServer.expect().get()
				.withPath(
						"/api/v1/namespaces/test/endpoints?labelSelector=l%3Dv&fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		mockServer.expect().get().withPath("/api/v1/endpoints?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().build();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, service)
				.always();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);
		properties.getMetadata().setAddLabels(false);
		properties.getMetadata().setAddAnnotations(false);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("10")).hasSize(1);
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePorts() {
		Map<String, String> labels = new HashMap();
		labels.put("l2", "v2");

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("20").endTargetRef().endAddress().addNewPort("mgmt", "mgmt_tcp", 900, "TCP")
				.addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint1);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		mockServer.expect().get().withPath(
				"/api/v1/namespaces/test/endpoints?labelSelector=l2%3Dv2&fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, service)
				.always();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setPrimaryPortName("http_tcp");

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("20")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void getEndPointsListTest() {
		Map<String, String> labels = new HashMap();
		labels.put("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("30").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		mockServer.expect().get()
				.withPath(
						"/api/v1/namespaces/test/endpoints?labelSelector=l%3Dv&fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);

		final KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<Endpoints> result_endpoints = discoveryClient.getEndPointsList("endpoint");

		assertThat(result_endpoints).hasSize(1);
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsMultipleAddresses() {
		Map<String, String> labels = new HashMap();
		labels.put("l1", "v1");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("40").endTargetRef().endAddress().addNewAddress().withIp("ip2").withNewTargetRef()
				.withUid("50").endTargetRef().endAddress().addNewPort("https", "https_tcp", 443, "TCP").endSubset()
				.build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoint);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		mockServer.expect().get().withPath(
				"/api/v1/namespaces/test/endpoints?labelSelector=l1%3Dv1&fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().build();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, service)
				.always();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);
		properties.getMetadata().setAddAnnotations(false);
		properties.getMetadata().setAddLabels(false);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2).filteredOn(ServiceInstance::isSecure).extracting(ServiceInstance::getHost)
				.containsOnly("ip1", "ip2");
	}

	@Test
	public void getServicesShouldReturnAllServicesWhenNoLabelsAreAppliedToTheClient() {
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services").andReturn(200, new ServiceListBuilder()
				.addNewItem().withNewMetadata().withName("s1").withLabels(new HashMap<String, String>() {
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
				.build()).once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2", "s3");
	}

	@Test
	public void getServicesShouldReturnOnlyMatchingServicesWhenLabelsAreAppliedToTheClient() {
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services?labelSelector=label%3Dvalue")
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
						}).endMetadata().endItem().build())
				.once();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				client -> client.services().withLabels(new HashMap<String, String>() {
					{
						put("label", "value");
					}
				}), new DefaultIsServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2");
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsFromMultipleNamespaces() {
		Endpoints endPoints1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("60")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		Endpoints endpoints2 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test2")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip2").withNewTargetRef().withUid("70")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		List<Endpoints> endpointsList = new ArrayList<>();
		endpointsList.add(endPoints1);
		endpointsList.add(endpoints2);

		EndpointsList endpoints = new EndpointsList();
		endpoints.setItems(endpointsList);

		mockServer.expect().get().withPath("/api/v1/endpoints?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, endpoints).once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/endpoint").andReturn(200, endPoints1)
				.once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test2/endpoints/endpoint").andReturn(200, endpoints2)
				.once();

		Service service1 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(new HashMap<String, String>() {
					{
						put("l", "v");
					}
				}).endMetadata().build();

		Service service2 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test2")
				.withLabels(new HashMap<String, String>() {
					{
						put("l", "v");
					}
				}).endMetadata().build();

		List<Service> servicesList = new ArrayList<>();
		servicesList.add(service1);
		servicesList.add(service2);

		ServiceList services = new ServiceList();
		services.setItems(servicesList);

		mockServer.expect().get().withPath("/api/v1/services?fieldSelector=metadata.name%3Dendpoint")
				.andReturn(200, services).once();

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, service1)
				.always();

		mockServer.expect().get().withPath("/api/v1/namespaces/test2/services/endpoint").andReturn(200, service2)
				.always();

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new DefaultIsServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2);
		assertThat(instances).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1);
		assertThat(instances).filteredOn(s -> s.getHost().equals("ip2") && !s.isSecure()).hasSize(1);
		assertThat(instances).filteredOn(s -> s.getServiceId().contains("endpoint")
				&& ((KubernetesServiceInstance) s).getNamespace().equals("test")).hasSize(1);
		assertThat(instances).filteredOn(s -> s.getServiceId().contains("endpoint")
				&& ((KubernetesServiceInstance) s).getNamespace().equals("test2")).hasSize(1);
		assertThat(instances).filteredOn(s -> s.getInstanceId().equals("60")).hasSize(1);
		assertThat(instances).filteredOn(s -> s.getInstanceId().equals("70")).hasSize(1);
	}

}
