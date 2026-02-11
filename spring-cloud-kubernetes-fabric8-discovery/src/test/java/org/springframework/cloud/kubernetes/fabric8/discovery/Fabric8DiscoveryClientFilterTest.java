/*
 * Copyright 2013-present the original author or authors.
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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryClientFilterTest extends Fabric8DiscoveryClientBase {

	private KubernetesClient mockClient;

	private static final String NAMESPACE = "test";

	@AfterEach
	void afterEach() {
		mockClient.services().inAnyNamespace().delete();
		mockClient.endpoints().inAnyNamespace().delete();
	}

	@Test
	void testFilteredServices() {
		List<String> springBootServiceNames = List.of("serviceA", "serviceB");
		List<Service> services = createSpringBootServiceByName(springBootServiceNames);

		// Add non spring boot service
		Service service = new Service();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("ServiceNonSpringBoot");
		objectMeta.setNamespace(NAMESPACE);
		service.setMetadata(objectMeta);
		services.add(service);
		mockClient.services().inNamespace(NAMESPACE).resource(service).create();
		Endpoints endpoints = endpoints(NAMESPACE, "ServiceNonSpringBoot", Map.of(), Map.of());
		mockClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "metadata.additionalProperties['spring-boot']", Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true, false, null);

		Fabric8DiscoveryClient fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE),
				mockClient);

		List<String> filteredServices = fabric8DiscoveryClient.getServices();
		assertThat(filteredServices).containsExactlyInAnyOrder("serviceA", "serviceB");

		// without filter
		properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60, false, null, Set.of(), Map.of(),
				null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true, false, null);

		fabric8DiscoveryClient = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);

		filteredServices = fabric8DiscoveryClient.getServices();
		assertThat(filteredServices).containsExactlyInAnyOrder("serviceA", "serviceB", "ServiceNonSpringBoot");

	}

	@Test
	void testFilteredServicesByPrefix() {
		List<String> springBootServiceNames = List.of("serviceA", "serviceB", "serviceC");
		List<Service> services = createSpringBootServiceByName(springBootServiceNames);

		// Add non spring boot service
		Service service = new Service();
		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setName("anotherService");
		service.setMetadata(objectMeta);
		services.add(service);
		mockClient.services().inNamespace(NAMESPACE).resource(service).create();
		Endpoints endpoints = endpoints(NAMESPACE, "anotherService", Map.of(), Map.of());
		mockClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "metadata.name.startsWith('service')", Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true, false, null);
		Fabric8DiscoveryClient client = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);
		List<String> filteredServices = client.getServices();
		assertThat(filteredServices).containsExactlyInAnyOrder("serviceA", "serviceB", "serviceC");

		properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60, false,
				"metadata.name.startsWith('another')", Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true, false, null);
		client = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);
		filteredServices = client.getServices();
		assertThat(filteredServices).containsExactlyInAnyOrder("anotherService");

	}

	@Test
	void testNoExpression() {
		List<String> springBootServiceNames = List.of("serviceA", "serviceB", "serviceC");
		createSpringBootServiceByName(springBootServiceNames);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true, false,
				null);
		Fabric8DiscoveryClient client = fabric8DiscoveryClient(properties, List.of(NAMESPACE), mockClient);
		List<String> filteredServices = client.getServices();
		assertThat(filteredServices).containsExactlyInAnyOrder("serviceA", "serviceB", "serviceC");

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

			mockClient.services().inNamespace(NAMESPACE).resource(service).create();
			Endpoints endpoints = endpoints(NAMESPACE, serviceName, Map.of(), Map.of());
			mockClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();
		}

		return serviceCollection;
	}

}
