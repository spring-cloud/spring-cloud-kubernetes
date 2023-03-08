/*
 * Copyright 2013-2022 the original author or authors.
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

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.APIGroupBuilder;
import io.fabric8.kubernetes.api.model.APIGroupList;
import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceList;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscovery;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * Tests that only assert the needed support for EndpointSlices in the cluster.
 *
 * @author wind57
 */
@EnableKubernetesMockClient
class Fabric8KubernetesCatalogWatchEndpointSlicesSupportTests {

	private static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito
			.mock(KubernetesNamespaceProvider.class);

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeEach
	void beforeEach() {
		mockServer.clearExpectations();
	}

	@Test
	void testEndpointSlicesEnabledButNotSupportedViaApiGroups() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, true);

		APIGroupList groupList = new APIGroupListBuilder().build();
		mockServer.expect().withPath("/apis").andReturn(200, groupList).always();

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	/**
	 * <pre>
	 *     - endpoint slices are enabled, but are not supported by the cluster, as such we will fail
	 *       with an IllegalArgumentException
	 *     - ApiVersions is empty
	 * </pre>
	 */
	@Test
	void testEndpointSlicesEnabledButNotSupportedViaApiVersions() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, true);

		GroupVersionForDiscovery forDiscovery = new GroupVersionForDiscoveryBuilder()
				.withGroupVersion("discovery.k8s.io/v1").build();
		APIGroup apiGroup = new APIGroupBuilder().withApiVersion("v1").withVersions(forDiscovery).build();
		APIGroupList groupList = new APIGroupListBuilder().withGroups(apiGroup).build();
		mockServer.expect().withPath("/apis").andReturn(200, groupList).always();

		APIResourceList apiResourceList = new APIResourceListBuilder().build();
		mockServer.expect().withPath("/apis/discovery.k8s.io/v1").andReturn(200, apiResourceList).always();

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	/**
	 * endpoint slices are disabled via properties, as such we will use a catalog watch
	 * based on Endpoints
	 */
	@Test
	void testEndpointsSupport() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, false);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, NAMESPACE_PROVIDER);

		Assertions.assertEquals(Fabric8EndpointsCatalogWatch.class, watch.stateGenerator().getClass());
	}

	/**
	 * endpoint slices are enabled via properties and supported by the cluster, as such we
	 * will use a catalog watch based on Endpoint Slices
	 */
	@Test
	void testEndpointSlicesSupport() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, true);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient, properties, NAMESPACE_PROVIDER);

		GroupVersionForDiscovery forDiscovery = new GroupVersionForDiscoveryBuilder()
				.withGroupVersion("discovery.k8s.io/v1").build();
		APIGroup apiGroup = new APIGroupBuilder().withApiVersion("v1").withVersions(forDiscovery).build();
		APIGroupList groupList = new APIGroupListBuilder().withGroups(apiGroup).build();
		mockServer.expect().withPath("/apis").andReturn(200, groupList).always();

		APIResource apiResource = new APIResourceBuilder().withGroup("discovery.k8s.io/v1").withKind("EndpointSlice")
				.build();
		APIResourceList apiResourceList = new APIResourceListBuilder().withResources(apiResource).build();
		mockServer.expect().withPath("/apis/discovery.k8s.io/v1").andReturn(200, apiResourceList).always();

		Assertions.assertEquals(Fabric8EndpointSliceV1CatalogWatch.class, watch.stateGenerator().getClass());
	}

}
