/*
 * Copyright 2012-2023 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.DiscoveryV1Api;
import io.kubernetes.client.openapi.models.V1Endpoint;
import io.kubernetes.client.openapi.models.V1EndpointSlice;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.discovery.catalog.KubernetesCatalogWatchContext.labelSelector;

/**
 * Implementation that is based on EndpointSlice V1.
 *
 * @author wind57
 */
final class KubernetesEndpointSlicesCatalogWatch
		implements Function<KubernetesCatalogWatchContext, List<EndpointNameAndNamespace>> {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesEndpointSlicesCatalogWatch.class));

	@Override
	public List<EndpointNameAndNamespace> apply(KubernetesCatalogWatchContext context) {

		List<V1EndpointSlice> endpointSlices;
		DiscoveryV1Api api = new DiscoveryV1Api(context.apiClient());

		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoint slices in all namespaces");
			endpointSlices = endpointSlices(api, context.properties().serviceLabels());
		}
		else if (!context.properties().namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoint slices in " + context.properties().namespaces());
			List<V1EndpointSlice> inner = new ArrayList<>(context.properties().namespaces().size());
			context.properties().namespaces().forEach(namespace -> inner
					.addAll(namespacedEndpointSlices(api, namespace, context.properties().serviceLabels())));
			endpointSlices = inner;
		}
		else {
			String namespace = KubernetesClientUtils.getApplicationNamespace(null, "catalog-watch",
					context.namespaceProvider());
			LOG.debug(() -> "discovering endpoint slices in namespace : " + namespace);
			endpointSlices = namespacedEndpointSlices(api, namespace, context.properties().serviceLabels());
		}

		Stream<V1ObjectReference> references = endpointSlices.stream().map(V1EndpointSlice::getEndpoints)
				.flatMap(List::stream).map(V1Endpoint::getTargetRef);

		return KubernetesCatalogWatchContext.state(references);

	}

	private List<V1EndpointSlice> endpointSlices(DiscoveryV1Api api, Map<String, String> labels) {
		try {
			return api.listEndpointSliceForAllNamespaces(null, null, null, labelSelector(labels), null, null, null,
					null, null, null, null).getItems();
		}
		catch (ApiException e) {
			LOG.warn(e, () -> "can not list endpoint slices in all namespaces");
			return Collections.emptyList();
		}
	}

	private List<V1EndpointSlice> namespacedEndpointSlices(DiscoveryV1Api api, String namespace,
			Map<String, String> labels) {
		try {
			return api.listNamespacedEndpointSlice(namespace, null, null, null, null, labelSelector(labels), null, null,
					null, null, null, null).getItems();
		}
		catch (ApiException e) {
			LOG.warn(e, () -> "can not list endpoint slices in namespace " + namespace);
			return Collections.emptyList();
		}
	}

}
