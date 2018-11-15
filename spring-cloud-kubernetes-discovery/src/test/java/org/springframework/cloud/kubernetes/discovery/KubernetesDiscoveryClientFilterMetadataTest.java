/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */
package org.springframework.cloud.kubernetes.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.DoneableEndpoints;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesDiscoveryClientFilterMetadataTest {

	@Mock
	private KubernetesClient kubernetesClient;

	@Mock
	private KubernetesDiscoveryProperties properties;

	@Mock
	private MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> serviceOperation;

	@Mock
	private MixedOperation<Endpoints, EndpointsList, DoneableEndpoints, Resource<Endpoints, DoneableEndpoints>> endpointsOperation;

	@Mock
	private Resource<Service, DoneableService> serviceResource;

	@Mock
	private Resource<Endpoints, DoneableEndpoints> endpointsResource;

	@InjectMocks
	private KubernetesDiscoveryClient underTest;

	@Test
	public void testBothLabelsAndAnnotationsDisabled() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(false);
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(false);

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "lab");
			}},
			new HashMap<String, String>() {{
				put("l1", "lab");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).isEmpty();
	}

	@Test
	public void testLabelsEnabledAndAnnotationsDisabled() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(true);
		when(properties.getLabelKeysPrefix()).thenReturn("");
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(false);

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "v1");
				put("l2", "v2");
			}},
			new HashMap<String, String>() {{
				put("l1", "lab");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l1", "v1"), entry("l2", "v2"));
	}

	@Test
	public void testLabelsEnabledAndAnnotationsDisabledWithPrefix() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(true);
		when(properties.getLabelKeysPrefix()).thenReturn("l_");
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(false);

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "v1");
				put("l2", "v2");
			}},
			new HashMap<String, String>() {{
				put("l1", "lab");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("l_l1", "v1"), entry("l_l2", "v2"));
	}

	@Test
	public void testLabelsDisabledAndAnnotationsEnabled() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(false);
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(true);
		when(properties.getAnnotationKeysPrefix()).thenReturn("");

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "v1");
			}},
			new HashMap<String, String>() {{
				put("a1", "v1");
				put("a2", "v2");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a1", "v1"), entry("a2", "v2"));
	}

	@Test
	public void testLabelsDisabledAndAnnotationsEnabledWithPrefix() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(false);
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(true);
		when(properties.getAnnotationKeysPrefix()).thenReturn("a_");

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "v1");
			}},
			new HashMap<String, String>() {{
				put("a1", "v1");
				put("a2", "v2");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(entry("a_a1", "v1"), entry("a_a2", "v2"));
	}

	@Test
	public void testBothLabelsAndAnnotationsEnabledWithPrefix() {
		final String serviceId = "s";

		when(properties.isEnabledAdditionOfLabelsAsMetadata()).thenReturn(true);
		when(properties.getLabelKeysPrefix()).thenReturn("l_");
		when(properties.isEnabledAdditionOfAnnotationsAsMetadata()).thenReturn(true);
		when(properties.getAnnotationKeysPrefix()).thenReturn("a_");

		setupServiceWithLabelsAndAnnotations(
			serviceId,
			new HashMap<String, String>() {{
				put("l1", "la1");
			}},
			new HashMap<String, String>() {{
				put("a1", "an1");
				put("a2", "an2");
			}}
		);

		final List<ServiceInstance> instances = underTest.getInstances(serviceId);
		assertThat(instances).hasSize(1);
		assertThat(instances.get(0).getMetadata()).containsOnly(
			entry("a_a1", "an1"), entry("a_a2", "an2"), entry("l_l1", "la1"));
	}

	private void setupServiceWithLabelsAndAnnotations(String serviceId,
		Map<String, String> labels, Map<String, String> annotations) {
		final Service service =
			new ServiceBuilder()
				.withNewMetadata()
				.withLabels(labels)
				.withAnnotations(annotations)
				.endMetadata()
			.build();
		when(serviceOperation.withName(serviceId)).thenReturn(serviceResource);
		when(serviceResource.get()).thenReturn(service);
		when(kubernetesClient.services()).thenReturn(serviceOperation);

		final Endpoints endpoints =
			new EndpointsBuilder()
				.addNewSubset()
				  .addNewPort()
			      .endPort()
			      .addNewAddress()
			      .endAddress()
			    .endSubset()
			.build();

		when(endpointsResource.get()).thenReturn(endpoints);
		when(endpointsOperation.withName(serviceId)).thenReturn(endpointsResource);
		when(kubernetesClient.endpoints()).thenReturn(endpointsOperation);
	}


}
