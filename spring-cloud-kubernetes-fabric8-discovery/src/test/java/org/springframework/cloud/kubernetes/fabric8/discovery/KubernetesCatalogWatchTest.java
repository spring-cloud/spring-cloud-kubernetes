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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleg Vyukov
 */
@RunWith(MockitoJUnitRunner.class)
public class KubernetesCatalogWatchTest {

	private static final KubernetesClient CLIENT = Mockito.mock(KubernetesClient.class);

	private final KubernetesCatalogWatch kubernetesCatalogWatch = new KubernetesCatalogWatch(CLIENT,
			KubernetesDiscoveryProperties.DEFAULT);

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> endpointsOperation;

	@Captor
	private ArgumentCaptor<HeartbeatEvent> heartbeatEventArgumentCaptor;

	@Before
	public void setUp() throws Exception {
		kubernetesCatalogWatch.setApplicationEventPublisher(this.applicationEventPublisher);
	}

	@Test
	public void testRandomOrderChangePods() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("other-pod", "api-pod"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testRandomOrderChangePodsAllNamespaces() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("other-pod", "api-pod"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testRandomOrderChangeServices() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createEndpointsListByServiceName("api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("other-service", "api-service"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testRandomOrderChangeServicesAllNamespaces() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createEndpointsListByServiceName("api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("other-service", "api-service"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEventBody() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(this.heartbeatEventArgumentCaptor.capture());

		HeartbeatEvent event = this.heartbeatEventArgumentCaptor.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<String> expectedPodsList = Arrays.asList("api-pod", "other-pod");
		assertThat(event.getValue()).isEqualTo(expectedPodsList);
	}

	@Test
	public void testEventBodyAllNamespaces() throws Exception {
		when(this.endpointsOperation.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"));
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(this.heartbeatEventArgumentCaptor.capture());

		HeartbeatEvent event = this.heartbeatEventArgumentCaptor.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<String> expectedPodsList = Arrays.asList("api-pod", "other-pod");
		assertThat(event.getValue()).isEqualTo(expectedPodsList);
	}

	@Test
	public void testEndpointsWithoutSubsets() {

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets();

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEndpointsWithoutSubsetsAllNamespaces() {

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets();

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEndpointsWithoutAddresses() {

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).setAddresses(null);

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEndpointsWithoutAddressesAllNamespaces() {

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).setAddresses(null);

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEndpointsWithoutTargetRefs() {

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).setTargetRef(null);

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	public void testEndpointsWithoutTargetRefsAllNamespaces() {

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).setTargetRef(null);

		when(this.endpointsOperation.list()).thenReturn(endpoints);
		when(CLIENT.endpoints()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace()).thenReturn(this.endpointsOperation);
		when(CLIENT.endpoints().inAnyNamespace().withLabels(anyMap())).thenReturn(this.endpointsOperation);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(this.applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}

	private EndpointsList createEndpointsListByServiceName(String... serviceNames) {
		List<Endpoints> endpoints = stream(serviceNames).map(s -> createEndpointsByPodName(s + "-singlePodUniqueId"))
				.collect(Collectors.toList());

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(endpoints);
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListWithoutSubsets() {
		Endpoints endpoints = new Endpoints();

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListByPodName(String... podNames) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsByPodName(podNames));

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private Endpoints createEndpointsByPodName(String podName) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsByPodName(podName));
		return endpoints;
	}

	private List<EndpointSubset> createSubsetsByPodName(String... names) {
		EndpointSubset endpointSubset = new EndpointSubset();
		endpointSubset.setAddresses(createEndpointAddressByPodNames(names));
		return Collections.singletonList(endpointSubset);
	}

	private List<EndpointAddress> createEndpointAddressByPodNames(String[] names) {
		return stream(names).map(name -> {
			ObjectReference podRef = new ObjectReference();
			podRef.setName(name);
			EndpointAddress endpointAddress = new EndpointAddress();
			endpointAddress.setTargetRef(podRef);
			return endpointAddress;
		}).collect(Collectors.toList());
	}

}
