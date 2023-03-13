/*
 * Copyright 2012-2022 the original author or authors.
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
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;

/**
 * Implementation that is based on EndpointSlice V1.
 *
 * @author wind57
 */
final class Fabric8EndpointSliceV1CatalogWatch
		implements Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8EndpointSliceV1CatalogWatch.class));

	@Override
	public List<EndpointNameAndNamespace> apply(Fabric8CatalogWatchContext context) {
		// take only pods that have endpoints
		List<EndpointSlice> endpointSlices;
		KubernetesClient client = context.kubernetesClient();

		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoint slices in all namespaces");
			endpointSlices = client.discovery().v1().endpointSlices().inAnyNamespace()
					.withLabels(context.properties().serviceLabels()).list().getItems();
		}
		else if (!context.properties().namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoint slices in " + context.properties().namespaces());
			List<EndpointSlice> inner = new ArrayList<>(context.properties().namespaces().size());
			context.properties().namespaces()
					.forEach(namespace -> inner.addAll(endpointSlices(context, namespace, client)));
			endpointSlices = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(context.kubernetesClient(), null, "catalog-watcher",
					context.namespaceProvider());
			LOG.debug(() -> "discovering endpoint slices in namespace : " + namespace);
			endpointSlices = endpointSlices(context, namespace, client);
		}

		Stream<ObjectReference> references = endpointSlices.stream().map(EndpointSlice::getEndpoints)
				.flatMap(List::stream).map(Endpoint::getTargetRef);

		return Fabric8CatalogWatchContext.state(references);

	}

	private List<EndpointSlice> endpointSlices(Fabric8CatalogWatchContext context, String namespace,
			KubernetesClient client) {
		return client.discovery().v1().endpointSlices().inNamespace(namespace)
				.withLabels(context.properties().serviceLabels()).list().getItems();
	}

}
