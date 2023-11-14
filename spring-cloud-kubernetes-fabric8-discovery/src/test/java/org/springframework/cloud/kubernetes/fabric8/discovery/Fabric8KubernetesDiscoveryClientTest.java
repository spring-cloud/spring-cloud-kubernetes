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
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesDiscoveryClientTest {

	private KubernetesClient mockClient;

	@BeforeEach
	void setup() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		mockClient.endpoints().inAnyNamespace().delete();
		mockClient.services().inAnyNamespace().delete();
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddress() {
		Map<String, String> labels = Map.of("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("10").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint").withNamespace("test").withLabels(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services, null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("10")).hasSize(1);
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePorts() {
		Map<String, String> labels = Map.of("l2", "v2");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("20").endTargetRef().endAddress().addNewPort("mgmt", "mgmt_tcp", 900, "TCP")
				.addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint").withNamespace("test").withLabels(labels).withAnnotations(labels)
				.endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), labels, "http_tcp", Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("20")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	void getEndPointsListTest() {
		Map<String, String> labels = Map.of("l", "v");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("30").endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset()
				.build();
		Service service = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();
		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services, null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<Endpoints> result_endpoints = discoveryClient.getEndPointsList("endpoint");

		assertThat(result_endpoints).hasSize(1);
	}

	@Test
	void getEndPointsListTestAllNamespaces() {

		String namespace1 = "ns1";
		String namespace2 = "ns2";

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace1)
				.endMetadata().build();
		Endpoints endPoint2 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace2)
				.endMetadata().build();

		Service service1 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace1)
				.endMetadata().build();
		Service service2 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace2)
				.endMetadata().build();

		mockClient.endpoints().inNamespace(namespace1).resource(endPoint1).create();
		mockClient.endpoints().inNamespace(namespace2).resource(endPoint2).create();
		mockClient.services().inNamespace(namespace1).resource(service1).create();
		mockClient.services().inNamespace(namespace2).resource(service2).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, x -> true, new ServicePortSecureResolver(properties));

		List<Endpoints> result_endpoints = discoveryClient.getEndPointsList("endpoint");

		assertThat(result_endpoints).hasSize(2);
	}

	@Test
	void getEndPointsListShouldHandleNamespaces() {

		String namespace1 = "ns1";
		String namespace2 = "ns2";
		String namespace3 = "ns3";

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace1)
				.endMetadata().build();
		Endpoints endPoint2 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace2)
				.endMetadata().build();
		Endpoints endPoint3 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace3)
				.endMetadata().build();

		Service service1 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace1)
				.endMetadata().build();
		Service service2 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace2)
				.endMetadata().build();
		Service service3 = new ServiceBuilder().withNewMetadata().withName("endpoint").withNamespace(namespace3)
				.endMetadata().build();

		mockClient.endpoints().inNamespace(namespace1).resource(endPoint1).create();
		mockClient.endpoints().inNamespace(namespace2).resource(endPoint2).create();
		mockClient.endpoints().inNamespace(namespace3).resource(endPoint3).create();
		mockClient.services().inNamespace(namespace1).resource(service1).create();
		mockClient.services().inNamespace(namespace2).resource(service2).create();
		mockClient.services().inNamespace(namespace3).resource(service3).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false,
				Set.of(namespace1, namespace3), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<Endpoints> result_endpoints = discoveryClient.getEndPointsList("endpoint");

		assertThat(result_endpoints).hasSize(2);
		assertThat(result_endpoints.stream().map(Endpoints::getMetadata).map(ObjectMeta::getNamespace)
				.collect(Collectors.toList())).containsOnly(namespace1, namespace3);
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsMultipleAddresses() {
		Map<String, String> labels = Map.of("l1", "v1");

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("40").endTargetRef().endAddress().addNewAddress().withIp("ip2").withNewTargetRef()
				.withUid("50").endTargetRef().endAddress().addNewPort("https", "https_tcp", 443, "TCP").endSubset()
				.build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint").withNamespace("test").withLabels(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		Metadata metadata = new Metadata(false, null, false, null, true, "port.");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), labels, null, metadata, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2).filteredOn(ServiceInstance::isSecure).extracting(ServiceInstance::getHost)
				.containsOnly("ip1", "ip2");
	}

	@Test
	void getServicesShouldReturnAllServicesWhenNoLabelsAreAppliedToTheClient() {

		Map<String, String> service1Labels = Collections.singletonMap("label", "value");
		Service service1 = new ServiceBuilder().withNewMetadata().withName("s1").withNamespace("test")
				.withLabels(service1Labels).endMetadata().build();

		Map<String, String> service2Labels = Map.of("label", "value", "label2", "value2");
		Service service2 = new ServiceBuilder().withNewMetadata().withName("s2").withNamespace("test")
				.withLabels(service2Labels).endMetadata().build();

		Service service3 = new ServiceBuilder().withNewMetadata().withName("s3").withNamespace("test").endMetadata()
				.build();
		mockClient.services().inNamespace("test").resource(service1).create();
		mockClient.services().inNamespace("test").resource(service2).create();
		mockClient.services().inNamespace("test").resource(service3).create();

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services, null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2", "s3");
	}

	@Test
	void getServicesShouldReturnOnlyMatchingServicesWhenLabelsAreAppliedToTheClient() {

		Map<String, String> service1Labels = Collections.singletonMap("label", "value");
		Service service1 = new ServiceBuilder().withNewMetadata().withName("s1").withNamespace("test")
				.withLabels(service1Labels).endMetadata().build();

		Map<String, String> service2Labels = Map.of("label", "value", "label2", "value2");
		Service service2 = new ServiceBuilder().withNewMetadata().withName("s2").withNamespace("test")
				.withLabels(service2Labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service1).create();
		mockClient.services().inNamespace("test").resource(service2).create();

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT,
				client -> client.services().withLabels(Collections.singletonMap("label", "value")), null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2");
	}

	@Test
	void getServicesShouldReturnServicesInNamespaces() {

		String nameSpace1 = "ns1";
		String nameSpace2 = "ns2";
		String nameSpace3 = "ns3";

		Service service1 = new ServiceBuilder().withNewMetadata().withName("s1").withNamespace(nameSpace1).endMetadata()
				.build();

		Service service2 = new ServiceBuilder().withNewMetadata().withName("s2").withNamespace(nameSpace2).endMetadata()
				.build();

		Service service3 = new ServiceBuilder().withNewMetadata().withName("s3").withNamespace(nameSpace3).endMetadata()
				.build();

		mockClient.services().inNamespace(nameSpace1).resource(service1).create();
		mockClient.services().inNamespace(nameSpace2).resource(service2).create();
		mockClient.services().inNamespace(nameSpace3).resource(service3).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false,
				Set.of(nameSpace1, nameSpace2), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<String> services = discoveryClient.getServices();

		assertThat(services).containsOnly("s1", "s2");
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsFromMultipleNamespaces() {
		Endpoints endPoints1 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef().withUid("60")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		Endpoints endPoints2 = new EndpointsBuilder().withNewMetadata().withName("endpoint").withNamespace("test2")
				.endMetadata().addNewSubset().addNewAddress().withIp("ip2").withNewTargetRef().withUid("70")
				.endTargetRef().endAddress().addNewPort("http", "http_tcp", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoints1).create();
		mockClient.endpoints().inNamespace("test2").resource(endPoints2).create();

		Service service1 = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint").withNamespace("test")
				.withLabels(Collections.singletonMap("l", "v")).endMetadata().build();

		Service service2 = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint").withNamespace("test2")
				.withLabels(Collections.singletonMap("l", "v")).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service1).create();
		mockClient.services().inNamespace("test2").resource(service2).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

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
	void instanceWithoutSubsetsShouldBeSkipped() {
		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint1").withNamespace("test")
				.withLabels(Collections.emptyMap()).endMetadata().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services, null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint1");

		assertThat(instances).isEmpty();
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePortsUsingPrimaryPortNameLabel() {
		Map<String, String> labels = Map.of("primary-port-name", "https");

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint2").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("80").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint1).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint2").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint2");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("80")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = Map.of("primary-port-name", "oops");

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint3").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("90").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http1", 80, "TCP")
				.addNewPort("http", "http2", 8080, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint1).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint3").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint3");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("90")).hasSize(1).hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = Map.of();

		Endpoints endPoint = new EndpointsBuilder().withNewMetadata().withName("endpoint4").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("100").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http1", 80, "TCP")
				.addNewPort("http", "http2", 8080, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint4").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), "oops", Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint4");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("100")).hasSize(1).hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttps() {
		Map<String, String> labels = Map.of();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("110").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint1).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint5").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("110")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttp() {
		Map<String, String> labels = Map.of();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("120").endTargetRef().endAddress().addNewPort("http", "https1", 443, "TCP")
				.addNewPort("http", "https2", 8443, "TCP").addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint1).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint5").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient,
				KubernetesDiscoveryProperties.DEFAULT, KubernetesClient::services, null,
				new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("120")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldLogWarning() {
		Map<String, String> labels = Map.of();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("130").endTargetRef().endAddress().addNewPort("http", "https", 443, "TCP")
				.addNewPort("http", "http", 80, "TCP").endSubset().build();

		mockClient.endpoints().inNamespace("test").resource(endPoint1).create();

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
				.withNewMetadata().withName("endpoint5").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").resource(service).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				true, null, Set.of(443, 8443), Map.of(), null, Metadata.DEFAULT, 0, true);

		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		// We're returning the first discovered port to not change previous behaviour
		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("130")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithoutPorts() {
		Map<String, String> labels = new HashMap<>();

		Endpoints endPoint1 = new EndpointsBuilder().withNewMetadata().withName("endpoint5").withNamespace("test")
				.withLabels(labels).endMetadata().addNewSubset().addNewAddress().withIp("ip1").withNewTargetRef()
				.withUid("130").endTargetRef().endAddress().endSubset().build();

		mockClient.endpoints().inNamespace("test").create(endPoint1);

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withNewMetadata().withName("endpoint5").withNamespace("test").withLabels(labels)
				.withAnnotations(labels).endMetadata().build();

		mockClient.services().inNamespace("test").create(service);

		final KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		final DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties,
				KubernetesClient::services, null, new ServicePortSecureResolver(properties));

		final List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		// We're returning the first discovered port to not change previous behaviour
		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getUri().toASCIIString().equals("http://ip1"))
				.filteredOn(s -> s.getInstanceId().equals("130")).hasSize(1).filteredOn(s -> 0 == s.getPort())
				.hasSize(1);
	}

}
