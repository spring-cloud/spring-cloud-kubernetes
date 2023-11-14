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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesDiscoveryClientTests {

	private static final SharedInformerFactoryStub STUB = new SharedInformerFactoryStub();

	private static final SharedInformerStub<V1Service> SERVICE_SHARED_INFORMER_STUB = new SharedInformerStub<>();

	private static final SharedInformerStub<V1Endpoints> ENDPOINTS_SHARED_INFORMER_STUB = new SharedInformerStub<>();

	private Cache<V1Service> servicesCache;

	private Lister<V1Service> servicesLister;

	private Cache<V1Endpoints> endpointsCache;

	private Lister<V1Endpoints> endpointsLister;

	@BeforeEach
	void beforeEach() {
		servicesCache = new Cache<>();
		servicesLister = new Lister<>(servicesCache);

		endpointsCache = new Cache<>();
		endpointsLister = new Lister<>(endpointsCache);
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddress() {
		Map<String, String> labels = Map.of("l", "v");
		String serviceId = "id";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("10");
		List<String> names = List.of("http");
		List<String> protocols = List.of("TCP");
		List<Integer> ports = List.of(80);
		List<String> appProtocols = List.of("appTCP");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, false,
				null, false, null);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("id");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("10")).hasSize(1);
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePorts() {
		Map<String, String> labels = Map.of("l2", "v2");
		String serviceId = "endpoint";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("20");
		List<String> names = List.of("http", "mgmt");
		List<String> protocols = List.of("TCP", "TCP");
		List<Integer> ports = List.of(80, 900);
		List<String> appProtocols = List.of("http_tcp", "mgmt_tcp");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), labels, "http_tcp", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("20")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsMultipleAddresses() {
		Map<String, String> labels = Map.of("l1", "v1");
		String serviceId = "endpoint";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1", "ip2");
		List<String> uuids = List.of("40", "50");
		List<String> names = List.of("https");
		List<String> protocols = List.of("TCP");
		List<Integer> ports = List.of(443);
		List<String> appProtocols = List.of("https_tcp");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(false, null, false,
				null, true, "port.");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), labels, null, metadata, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint");

		assertThat(instances).hasSize(2).filteredOn(ServiceInstance::isSecure).extracting(ServiceInstance::getHost)
				.containsOnly("ip1", "ip2");
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsFromMultipleNamespaces() {

		Map<String, String> labels = Map.of("l", "v");
		String serviceId = "endpoint";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("60");
		List<String> names = List.of("http");
		List<String> protocols = List.of("TCP");
		List<Integer> ports = List.of(80);
		List<String> appProtocols = List.of("https_tcp");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		ips = List.of("ip2");
		uuids = List.of("70");
		namespace = "test2";

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

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
		V1Endpoints endpoints = new V1EndpointsBuilder().withNewMetadata().withName("endpoint1").withNamespace("test")
				.withLabels(Collections.emptyMap()).endMetadata().build();
		endpointsCache.add(endpoints);

		V1Service service = new V1ServiceBuilder().withNewMetadata().withName("endpoint1").withNamespace("test").and()
				.build();
		servicesCache.add(service);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint1");

		assertThat(instances).isEmpty();
	}

	@Test
	void getInstancesShouldBeAbleToHandleEndpointsSingleAddressAndMultiplePortsUsingPrimaryPortNameLabel() {
		Map<String, String> labels = Map.of("primary-port-name", "https");
		String serviceId = "endpoint2";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("80");
		List<String> names = List.of("http", "https");
		List<String> protocols = List.of("TCP", "TCP");
		List<Integer> ports = List.of(80, 443);
		List<String> appProtocols = List.of("http", "https");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint2");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("80")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = Map.of("primary-port-name", "oops");
		String serviceId = "endpoint3";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("90");
		List<String> names = List.of("httpA", "httpB", "httpC", "httpD");
		List<String> protocols = List.of("TCP", "TCP", "TCP", "TCP");
		List<Integer> ports = List.of(8443, 443, 80, 8080);
		List<String> appProtocols = List.of("https1", "https2", "http1", "http2");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint3");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("90")).hasSize(1).hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameWithoutFallbackShouldLogWarning() {
		Map<String, String> labels = Map.of();
		String serviceId = "endpoint4";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("100");
		List<String> names = List.of("httpA", "httpB", "httpC", "httpD");
		List<String> protocols = List.of("TCP", "TCP", "TCP", "TCP");
		List<Integer> ports = List.of(8443, 443, 80, 8080);
		List<String> appProtocols = List.of("https1", "https2", "http1", "http2");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), "oops", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint4");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("100")).hasSize(1).hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttps() {
		Map<String, String> labels = Map.of();
		String serviceId = "endpoint5";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("110");
		List<String> names = List.of("httpA", "httpB");
		List<String> protocols = List.of("TCP", "TCP");
		List<Integer> ports = List.of(443, 80);
		List<String> appProtocols = List.of("http", "https");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("110")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttp() {
		Map<String, String> labels = Map.of();
		String serviceId = "endpoint5";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("120");
		List<String> names = List.of("httpA", "httpB", "httpC");
		List<String> protocols = List.of("http", "http", "http");
		List<Integer> ports = List.of(80, 8443, 80);
		List<String> appProtocols = List.of("TCP", "TCP", "TCP");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB,
				KubernetesDiscoveryProperties.DEFAULT);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("120")).hasSize(1).filteredOn(s -> 80 == s.getPort())
				.hasSize(1);
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldLogWarning() {
		Map<String, String> labels = Map.of();
		String serviceId = "endpoint5";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("130");
		List<String> names = List.of("http", "https");
		List<String> protocols = List.of("http", "https");
		List<Integer> ports = List.of(80, 443);
		List<String> appProtocols = List.of("TCP", "TCP");

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				true, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		// We're returning the first discovered port to not change previous behaviour
		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getInstanceId().equals("130")).hasSize(1).filteredOn(s -> 443 == s.getPort())
				.hasSize(1);
	}

	@Test
	public void instanceWithoutPorts() {
		Map<String, String> labels = Map.of();
		String serviceId = "endpoint5";
		String serviceType = "ExternalName";
		String namespace = "test";
		List<String> ips = List.of("ip1");
		List<String> uuids = List.of("130");
		List<String> names = List.of();
		List<String> protocols = List.of();
		List<Integer> ports = List.of();
		List<String> appProtocols = List.of();

		setup(serviceId, serviceType, namespace, labels, ips, uuids, names, protocols, ports, appProtocols);

		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(STUB, servicesLister,
				endpointsLister, SERVICE_SHARED_INFORMER_STUB, ENDPOINTS_SHARED_INFORMER_STUB, properties);

		List<ServiceInstance> instances = discoveryClient.getInstances("endpoint5");

		// We're returning the first discovered port to not change previous behaviour
		assertThat(instances).hasSize(1).filteredOn(s -> s.getHost().equals("ip1") && !s.isSecure()).hasSize(1)
				.filteredOn(s -> s.getUri().toASCIIString().equals("http://ip1"))
				.filteredOn(s -> s.getInstanceId().equals("130")).hasSize(1).filteredOn(s -> 0 == s.getPort())
				.hasSize(1);
	}

	private void setup(String serviceId, String serviceType, String namespace, Map<String, String> labels,
			List<String> ips, List<String> uuids, List<String> names, List<String> protocols, List<Integer> ports,
			List<String> appProtocols) {

		V1Service service = new V1ServiceBuilder().withSpec(new V1ServiceSpecBuilder().withType(serviceType).build())
				.withNewMetadata().withName(serviceId).withNamespace(namespace).withLabels(labels).endMetadata()
				.build();

		servicesCache.add(service);

		V1ObjectMeta objectMeta = new V1ObjectMeta();
		objectMeta.setNamespace(namespace);
		objectMeta.setName(serviceId);

		V1Endpoints endpoints = new V1EndpointsBuilder().withNewMetadata().withName(serviceId).withNamespace(namespace)
				.withLabels(labels).endMetadata().build();

		List<V1EndpointAddress> addresses = new ArrayList<>();
		for (int i = 0; i < ips.size(); ++i) {
			V1EndpointAddress address = new V1EndpointAddressBuilder().withIp(ips.get(i)).withNewTargetRef()
					.withUid(uuids.get(i)).endTargetRef().build();
			addresses.add(address);
		}

		V1EndpointSubset subset = new V1EndpointSubset();
		subset.setAddresses(addresses);

		List<CoreV1EndpointPort> corePorts = new ArrayList<>();
		for (int i = 0; i < names.size(); ++i) {
			CoreV1EndpointPort port = new CoreV1EndpointPortBuilder().withName(names.get(i))
					.withProtocol(protocols.get(i)).withPort(ports.get(i)).withAppProtocol(appProtocols.get(i)).build();
			corePorts.add(port);
		}
		subset.setPorts(corePorts);
		endpoints.setSubsets(List.of(subset));

		endpointsCache.add(endpoints);

	}

}
