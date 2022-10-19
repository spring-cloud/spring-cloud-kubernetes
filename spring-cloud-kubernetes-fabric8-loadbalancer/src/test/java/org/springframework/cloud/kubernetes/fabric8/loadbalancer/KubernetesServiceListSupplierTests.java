/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KubernetesServiceListSupplierTests {

	@Mock
	Environment environment;

	@Mock
	Fabric8ServiceInstanceMapper mapper;

	@Mock
	KubernetesClient client;

	@Mock
	MixedOperation<Service, ServiceList, ServiceResource<Service>> serviceOperation;

	@Mock
	NonNamespaceOperation<Service, ServiceList, ServiceResource<Service>> namespaceOperation;

	@Mock
	ServiceResource<Service> serviceResource;

	@Mock
	FilterWatchListMultiDeletable<Service, ServiceList> multiDeletable;

	@Test
	void testPositiveMatch() {
		when(environment.getProperty("loadbalancer.client.name")).thenReturn("test-service");
		when(mapper.map(any(Service.class)))
				.thenReturn(new DefaultKubernetesServiceInstance("", "", "", 0, null, false));
		when(this.client.getNamespace()).thenReturn("test");
		when(this.client.services()).thenReturn(this.serviceOperation);
		when(this.serviceOperation.inNamespace("test")).thenReturn(namespaceOperation);
		when(this.namespaceOperation.withName("test-service")).thenReturn(this.serviceResource);
		when(this.serviceResource.get()).thenReturn(buildService("test-service", 8080));
		KubernetesServicesListSupplier supplier = new Fabric8ServicesListSupplier(environment, client, mapper,
				KubernetesDiscoveryProperties.DEFAULT);
		List<ServiceInstance> instances = supplier.get().blockFirst();
		assert instances != null;
		Assertions.assertEquals(1, instances.size());
	}

	@Test
	void testPositiveMatchAllNamespaces() {
		when(environment.getProperty("loadbalancer.client.name")).thenReturn("test-service");
		when(mapper.map(any(Service.class)))
				.thenReturn(new DefaultKubernetesServiceInstance("", "", "", 0, null, false));
		when(this.client.services()).thenReturn(this.serviceOperation);
		when(this.serviceOperation.inAnyNamespace()).thenReturn(this.multiDeletable);
		when(this.multiDeletable.withField("metadata.name", "test-service")).thenReturn(this.multiDeletable);
		ServiceList serviceList = new ServiceList();
		serviceList.getItems().add(buildService("test-service", 8080));
		when(this.multiDeletable.list()).thenReturn(serviceList);
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, true, true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0);
		KubernetesServicesListSupplier supplier = new Fabric8ServicesListSupplier(environment, client, mapper,
				discoveryProperties);
		List<ServiceInstance> instances = supplier.get().blockFirst();
		assert instances != null;
		Assertions.assertEquals(1, instances.size());
	}

	private Service buildService(String name, int port) {
		return new ServiceBuilder().withNewMetadata().withName(name).endMetadata().withNewSpec().addNewPort()
				.withPort(port).endPort().endSpec().build();
	}

}
