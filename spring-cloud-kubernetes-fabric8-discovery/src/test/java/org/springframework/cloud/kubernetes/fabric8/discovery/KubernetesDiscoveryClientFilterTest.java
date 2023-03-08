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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class KubernetesDiscoveryClientFilterTest {

	private final KubernetesClient kubernetesClient = Mockito.mock(KubernetesClient.class);

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction = KubernetesClient::services;

	private final MixedOperation<Service, ServiceList, ServiceResource<Service>> serviceOperation = Mockito
			.mock(MixedOperation.class);

	@Test
	void testFilteredServices() {
		List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB");
		List<Service> services = createSpringBootServiceByName(springBootServiceNames);

		// Add non spring boot service
		Service service = new Service();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("ServiceNonSpringBoot");
		service.setMetadata(objectMeta);
		services.add(service);

		ServiceList serviceList = new ServiceList();
		serviceList.setItems(services);
		when(this.serviceOperation.list()).thenReturn(serviceList);
		when(this.kubernetesClient.services()).thenReturn(this.serviceOperation);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "metadata.additionalProperties['spring-boot']", Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		KubernetesDiscoveryClient client = new KubernetesDiscoveryClient(this.kubernetesClient, properties,
				this.kubernetesClientServicesFunction);

		List<String> filteredServices = client.getServices();
		assertThat(filteredServices).isEqualTo(springBootServiceNames);

	}

	@Test
	void testFilteredServicesByPrefix() {
		List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB", "serviceC");
		List<Service> services = createSpringBootServiceByName(springBootServiceNames);

		// Add non spring boot service
		Service service = new Service();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("anotherService");
		service.setMetadata(objectMeta);
		services.add(service);

		ServiceList serviceList = new ServiceList();
		serviceList.setItems(services);
		when(this.serviceOperation.list()).thenReturn(serviceList);
		when(this.kubernetesClient.services()).thenReturn(this.serviceOperation);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "metadata.name.startsWith('service')", Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		KubernetesDiscoveryClient client = new KubernetesDiscoveryClient(this.kubernetesClient, properties,
				this.kubernetesClientServicesFunction);

		List<String> filteredServices = client.getServices();
		assertThat(filteredServices).isEqualTo(springBootServiceNames);

	}

	@Test
	void testNoExpression() {
		List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB", "serviceC");
		List<Service> services = createSpringBootServiceByName(springBootServiceNames);

		ServiceList serviceList = new ServiceList();
		serviceList.setItems(services);
		when(this.serviceOperation.list()).thenReturn(serviceList);
		when(this.kubernetesClient.services()).thenReturn(this.serviceOperation);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		KubernetesDiscoveryClient client = new KubernetesDiscoveryClient(this.kubernetesClient, properties,
				this.kubernetesClientServicesFunction);

		List<String> filteredServices = client.getServices();

		assertThat(filteredServices).isEqualTo(springBootServiceNames);

	}

	private List<Service> createSpringBootServiceByName(List<String> serviceNames) {
		List<Service> serviceCollection = new ArrayList<>(serviceNames.size());
		for (String serviceName : serviceNames) {
			Service service = new Service();
			ObjectMeta objectMeta = new ObjectMeta();
			objectMeta.setName(serviceName);
			objectMeta.setAdditionalProperty("spring-boot", "true");
			service.setMetadata(objectMeta);
			serviceCollection.add(service);
		}
		return serviceCollection;
	}

}
