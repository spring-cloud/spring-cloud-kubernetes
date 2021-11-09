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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@EnableKubernetesMockClient(crud = true, https = false)
public class KubernetesDiscoveryClientTest {

	private KubernetesClient mockClient;

	@BeforeEach
	public void setup() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	public void after() {
		mockClient.close();
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddress() {
		Map<String, String> labels = new HashMap<>();
		labels.put("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("10").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);
		properties.getMetadata().setAddLabels(false);
		properties.getMetadata().setAddAnnotations(false);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("10")).hasSize(1);
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePorts() {
		Map<String, String> labels = new HashMap<>();
		labels.put("l2", "v2");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("20").endTargetRef().endAddress().addNewPort("mgmt", "mgmt_tcp", 900, "TCP")
				.addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setPrimaryPortName("http_tcp");

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("20")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void getEndPointsListTest() {
		Map<String, String> labels = new HashMap<>();
		labels.put("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("30").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);

		final KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<Endpoints> result_endpoints = discoveryClient.getEndPointsList("endpoint");

		assertThat(result_endpoints).hasSize(1);
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsMultipleAddresses() {
		Map<String, String> labels = new HashMap<>();
		labels.put("l1", "v1");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("40").endTargetRef().endAddress().addNewAddress().withIp("ip2").withNewTargetRef()
				.withUid("50").endTargetRef().endAddress().addNewPort("https", "https_tcp", 443, "TCP").endSubset()
				.build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setServiceLabels(labels);
		properties.getMetadata().setAddAnnotations(false);
		properties.getMetadata().setAddLabels(false);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2).filteredOn(ServiceInstance::isSecure).extracting(ServiceInstance::getHost)
				.containsOnly("ip1", "ip2");
	}

	@Test
	public void getServicesShouldReturnAllServicesWhenNoLabelsAreAppliedToTheClient() {

		Map<String, String> service1Labels = Collections.singletonMap("label", "value");
		Service service1 = new ServiceBuilder().withNewMetadata().withName("s1").withNamespace("test")
				.withLabels(service1Labels).endMetadata().build();

		Map<String, String> service2Labels = new HashMap<>();
		service2Labels.put("label", "value");
		service2Labels.put("label2", "value2");
		Service service2 = new ServiceBuilder().withNewMetadata().withName("s2").withNamespace("test")
				.withLabels(service2Labels).endMetadata().build();

		Service service3 = new ServiceBuilder().withNewMetadata().withName("s3").withNamespace("test").endMetadata()
				.build();

		mockClient.services().inNamespace("test").create(service1);
		mockClient.services().inNamespace("test").create(service2);
		mockClient.services().inNamespace("test").create(service3);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2", "s3");
	}

	@Test
	public void getServicesShouldReturnOnlyMatchingServicesWhenLabelsAreAppliedToTheClient() {

		Map<String, String> service1Labels = Collections.singletonMap("label", "value");
		Service service1 = new ServiceBuilder().withNewMetadata().withName("s1").withNamespace("test")
				.withLabels(service1Labels).endMetadata().build();

		Map<String, String> service2Labels = new HashMap<>();
		service2Labels.put("label", "value");
		service2Labels.put("label2", "value2");
		Service service2 = new ServiceBuilder().withNewMetadata().withName("s2").withNamespace("test")
				.withLabels(service2Labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service1);
		mockClient.services().inNamespace("test").create(service2);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				client -> client.services().withLabels(Collections.singletonMap("label", "value")),
				new ServicePortSecureResolver(properties));

		final List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2");
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsFromMultipleNamespaces() {
		Endpoints endPoints1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("60")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		Endpoints endPoints2 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test2")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip2").withNewTargetRef().withUid("70")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoints1);
		mockClient.endpoints().inNamespace("test2").create(endPoints2);

		Service service1 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(Collections.singletonMap("l", "v")).endMetadata().build();

		Service service2 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test2")
				.withLabels(Collections.singletonMap("l", "v")).endMetadata().build();

		mockClient.services().inNamespace("test").create(service1);
		mockClient.services().inNamespace("test2").create(service2);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

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

	@Test
	public void instanceWithoutPortsShouldBeSkipped() {
		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint1").withNamespace("test")
				.withLabels(Collections.emptyMap()).endMetadata().build();

		mockClient.endpoints().inNamespace("test").create(endPoint);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint1");

		assertThat(instances).isEmpty();
	}

	@Test
	public void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePortsUsingPrimaryPortNameLabel() {
		Map<String, String> labels = new HashMap<>();
		labels.put("primary-port-name", "https");

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint2").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("80").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint2").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint2");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("80")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = new HashMap<>();
		labels.put("primary-port-name", "oops");

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint3").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("90").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http1", 80, "TCP")
				.addNewPort("http", "http2", 8080, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint3").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint3");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("90")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = new HashMap<>();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint4").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("100").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http1", 80, "TCP")
				.addNewPort("http", "http2", 8080, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint4").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setPrimaryPortName("oops");

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint4");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("100")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttps() {
		Map<String, String> labels = new HashMap<>();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("110").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("110")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	//
	@Test
	public void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttp() {
		Map<String, String> labels = new HashMap<>();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("120").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("120")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldLogWarning() {
		Map<String, String> labels = new HashMap<>();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("130").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		// We're returning the first discovered port to not change previous behaviour
		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("130")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

}
