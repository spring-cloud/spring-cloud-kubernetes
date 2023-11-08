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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.discovery.catalog.KubernetesCatalogWatchContext.labelSelector;

/**
 * Implementation that is based on V1Endpoints.
 *
 * @author wind57
 */
final class KubernetesEndpointsCatalogWatch
		implements Function<KubernetesCatalogWatchContext, List<EndpointNameAndNamespace>> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesEndpointsCatalogWatch.class));

	@Override
	public List<EndpointNameAndNamespace> apply(KubernetesCatalogWatchContext context) {
		List<V1Endpoints> endpoints;
		CoreV1Api coreV1Api = context.coreV1Api();
		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			endpoints = endpoints(coreV1Api, context.properties().serviceLabels());
		}
		else if (!context.properties().namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in " + context.properties().namespaces());
			List<V1Endpoints> inner = new ArrayList<>(context.properties().namespaces().size());
			context.properties().namespaces().forEach(namespace -> inner
					.addAll(namespacedEndpoints(coreV1Api, namespace, context.properties().serviceLabels())));
			endpoints = inner;
		}
		else {
			String namespace = KubernetesClientUtils.getApplicationNamespace(null, "catalog-watch",
					context.namespaceProvider());
			LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
			endpoints = namespacedEndpoints(coreV1Api, namespace, context.properties().serviceLabels());
		}

		/**
		 * <pre>
		 *   - An "V1Endpoints" holds a List of V1EndpointSubset.
		 *   - A single V1EndpointSubset holds a List of V1EndpointAddress
		 *
		 *   - (The union of all V1EndpointSubsets is the Set of all V1Endpoints)
		 *   - Set of V1Endpoints is the cartesian product of :
		 *     V1EndpointSubset::getAddresses and V1EndpointSubset::getPorts (each is a List)
		 * </pre>
		 */
		Stream<V1ObjectReference> references = endpoints.stream().map(V1Endpoints::getSubsets).filter(Objects::nonNull)
				.flatMap(List::stream).map(V1EndpointSubset::getAddresses).filter(Objects::nonNull)
				.flatMap(List::stream).map(V1EndpointAddress::getTargetRef);

		return KubernetesCatalogWatchContext.state(references);

	}

	private List<V1Endpoints> endpoints(CoreV1Api client, Map<String, String> labels) {
		try {
			return client.listEndpointsForAllNamespaces(null, null, null, labelSelector(labels), null, null, null, null,
					null, null, null).getItems();
		}
		catch (ApiException e) {
			LOG.warn(e, () -> "can not list endpoints in all namespaces");
			return Collections.emptyList();
		}
	}

	private List<V1Endpoints> namespacedEndpoints(CoreV1Api client, String namespace, Map<String, String> labels) {
		try {
			return client.listNamespacedEndpoints(namespace, null, null, null, null, labelSelector(labels), null, null,
					null, null, null, null).getItems();
		}
		catch (ApiException e) {
			LOG.warn(e, () -> "can not list endpoints in namespace " + namespace);
			return Collections.emptyList();
		}
	}

}
