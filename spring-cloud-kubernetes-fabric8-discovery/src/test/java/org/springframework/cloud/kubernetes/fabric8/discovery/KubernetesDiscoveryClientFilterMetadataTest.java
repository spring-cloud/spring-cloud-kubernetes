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

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.assertj.core.util.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesDiscoveryClientFilterMetadataTest {

	@Mock
	private KubernetesClient kubernetesClient;

	@Mock
	private KubernetesDiscoveryProperties properties;

	@Mock
	private ServicePortSecureResolver isServicePortSecureResolver;

	@Mock
	private MixedOperation<Service, ServiceList, ServiceResource<Service>> serviceOperation;

	@Mock
	private MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> endpointsOperation;

	@Mock
	private ServiceResource<Service> serviceResource;

	@Mock
	private Resource<Endpoints> endpointsResource;

	@Mock
	FilterWatchListDeletable<Endpoints, EndpointsList> filter;

	@InjectMocks
	private KubernetesDiscoveryClient underTest;

	@Test
	public void testAllExtraMetadataDisabled() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, false, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "lab");
			}
		}, new HashMap<String, String>() {
			{
				put("l1", "lab");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).isEmpty();
	}

	@Test
	public void testLabelsEnabled() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(true, null, false, null, false, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
				put("l2", "v2");
			}
		}, new HashMap<String, String>() {
			{
				put("l1", "lab");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l1", "v1"), entry("l2", "v2"));
	}

	@Test
	public void testLabelsEnabledWithPrefix() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(true, "l_", false, null, false, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
				put("l2", "v2");
			}
		}, new HashMap<String, String>() {
			{
				put("l1", "lab");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l_l1", "v1"), entry("l_l2", "v2"));
	}

	@Test
	public void testAnnotationsEnabled() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(false, null, true, null, false, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
			}
		}, new HashMap<String, String>() {
			{
				put("a1", "v1");
				put("a2", "v2");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a1", "v1"), entry("a2", "v2"));
	}

	@Test
	public void testAnnotationsEnabledWithPrefix() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(false, null, true, "a_", false, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
			}
		}, new HashMap<String, String>() {
			{
				put("a1", "v1");
				put("a2", "v2");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "v1"), entry("a_a2", "v2"));
	}

	@Test
	public void testPortsEnabled() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, true, null);
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
			}
		}, new HashMap<String, String>() {
			{
				put("a1", "v1");
				put("a2", "v2");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("http", "80"));
	}

	@Test
	public void testPortsEnabledWithPrefix() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(false, null, false, null, true, "p_");
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "v1");
			}
		}, new HashMap<String, String>() {
			{
				put("a1", "v1");
				put("a2", "v2");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("p_http", "80"));
	}

	@Test
	public void testLabelsAndAnnotationsAndPortsEnabledWithPrefix() {
		final String serviceId = "s";

		Metadata metadata = new Metadata(true, "l_", true, "a_", true, "p_");
		when(this.properties.getMetadata()).thenReturn(metadata);

		setupServiceWithLabelsAndAnnotationsAndPorts(serviceId, "ns", new HashMap<String, String>() {
			{
				put("l1", "la1");
			}
		}, new HashMap<String, String>() {
			{
				put("a1", "an1");
				put("a2", "an2");
			}
		}, new HashMap<Integer, String>() {
			{
				put(80, "http");
				put(5555, "");
			}
		});

		final List<ServiceInstance> instances = this.underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "an1"), entry("a_a2", "an2"),
				entry("l_l1", "la1"), entry("p_http", "80"));
	}

	private void setupServiceWithLabelsAndAnnotationsAndPorts(String serviceId, String namespace,
			Map<String, String> labels, Map<String, String> annotations, Map<Integer, String> ports) {
		final Service service = new ServiceBuilder().withNewMetadata().withNamespace(namespace).withLabels(labels)
				.withAnnotations(annotations).endMetadata().withNewSpec().withPorts(getServicePorts(ports)).endSpec()
				.build();
		when(this.serviceOperation.withName(serviceId)).thenReturn(this.serviceResource);
		when(this.serviceResource.get()).thenReturn(service);
		when(this.kubernetesClient.services()).thenReturn(this.serviceOperation);
		when(this.kubernetesClient.services().inNamespace(anyString())).thenReturn(this.serviceOperation);

		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setNamespace(namespace);

		final Endpoints endpoints = new EndpointsBuilder().withMetadata(objectMeta).addNewSubset()
				.addAllToPorts(getEndpointPorts(ports)).addNewAddress().endAddress().endSubset().build();

		when(this.kubernetesClient.endpoints()).thenReturn(this.endpointsOperation);

		EndpointsList endpointsList = new EndpointsList(null, Collections.singletonList(endpoints), null, null);
		when(filter.list()).thenReturn(endpointsList);
		when(filter.withLabels(anyMap())).thenReturn(filter);

		when(this.kubernetesClient.endpoints().withField(eq("metadata.name"), eq(serviceId))).thenReturn(filter);

	}

	private List<ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			ServicePortBuilder servicePortBuilder = new ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

	private List<EndpointPort> getEndpointPorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			EndpointPortBuilder endpointPortBuilder = new EndpointPortBuilder();
			endpointPortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				endpointPortBuilder.withName(e.getValue());
			}
			return endpointPortBuilder.build();
		}).collect(toList());
	}

}
