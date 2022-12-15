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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

/**
 * make sure that all the tests for endpoints are also handled by endpoint slices
 *
 * @author wind57
 */
abstract class KubernetesEndpointsAndEndpointSlicesTests {

	static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito.mock(KubernetesNamespaceProvider.class);

	static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
		.forClass(HeartbeatEvent.class);

	static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito.mock(ApplicationEventPublisher.class);

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We set the namespace to be "namespaceA" and search for labels {color=blue}
	 *     As a result only one pod is taken: podB
	 *
	 * </pre>
	 */
	abstract void testInSpecificNamespaceWithServiceLabels();

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We set the namespace to be "namespaceA" and search without labels
	 *     As a result we get three pods:
	 *       - podA in namespaceA
	 *       - podB in namespaceA
	 *       - pocC in namespaceA
	 *
	 * </pre>
	 */
	abstract void testInSpecificNamespaceWithoutServiceLabels();

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We search in all namespaces with labels {color=blue}
	 *     As a result two pods are taken:
	 *       - podB in namespaceA
	 *       - podD in namespaceB
	 *
	 * </pre>
	 */
	abstract void testInAllNamespacesWithServiceLabels();

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We search in all namespaces without labels
	 *     As a result we get all 5 pods
	 *
	 * </pre>
	 */
	abstract void testInAllNamespacesWithoutServiceLabels();

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - namespaces = [namespaceB]
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Even if namespaces = [namespaceB], we still take podB and podD, because all-namespace=true
	 *
	 * </pre>
	 */
	abstract void testAllNamespacesTrueOtherBranchesNotCalled();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = [namespaceA]
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [namespaceA], we wil take podB, because all-namespace=false (podD is not part of the response)
	 *
	 * </pre>
	 */
	abstract void testAllNamespacesFalseNamespacesPresent();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = []
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [], we wil take podB, because all-namespace=false (podD is not part of the response)
	 *
	 * </pre>
	 */
	abstract void testAllNamespacesFalseNamespacesNotPresent();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = [namespaceA, namespaceB]
	 *
	 *     - we have 7 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 * 	   - podF in namespaceB with labels {color=blue}
	 * 	   - podO in namespaceC with labels {color=blue}
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [namespaceA, namespaceB], we wil take podB, podD and podF,
	 *     but will not take podO
	 *
	 * </pre>
	 */
	abstract void testTwoNamespacesOutOfThree();

	KubernetesCatalogWatch createWatcherInSpecificNamespaceWithLabels(String namespace, Map<String, String> labels,
			boolean endpointSlices) {

		when(NAMESPACE_PROVIDER.getNamespace()).thenReturn(namespace);

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
			Set.of(namespace), true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new Fabric8EndpointSliceV1CatalogWatch()).when(watch).stateGenerator();
		}

		watch.postConstruct();
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}
}
