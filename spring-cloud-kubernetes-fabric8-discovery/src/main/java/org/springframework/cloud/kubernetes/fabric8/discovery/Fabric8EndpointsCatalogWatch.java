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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;

/**
 * Implementation that is based on Endpoints.
 *
 * @author wind57
 */
final class Fabric8EndpointsCatalogWatch
		implements Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8EndpointsCatalogWatch.class));

	@Override
	public List<EndpointNameAndNamespace> apply(Fabric8CatalogWatchContext context) {
		List<Endpoints> endpoints;
		KubernetesClient client = context.kubernetesClient();

		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
				endpoints = client.endpoints().inAnyNamespace().withLabels(context.properties().serviceLabels()).list()
						.getItems();
		}
		else if (!context.properties().namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in " + context.properties().namespaces());
			List<Endpoints> inner = new ArrayList<>(context.properties().namespaces().size());
			context.properties().namespaces().forEach(namespace -> inner.addAll(endpoints(context, namespace, client)));
			endpoints = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(context.kubernetesClient(), null, "catalog-watcher",
					context.namespaceProvider());
			LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
			endpoints = endpoints(context, namespace, client);
		}

		/**
		 * <pre>
		 *   - An "Endpoints" holds a List of EndpointSubset.
		 *   - A single EndpointSubset holds a List of EndpointAddress
		 *
		 *   - (The union of all EndpointSubsets is the Set of all Endpoints)
		 *   - Set of Endpoints is the cartesian product of :
		 *     EndpointSubset::getAddresses and EndpointSubset::getPorts (each is a List)
		 * </pre>
		 */
		Stream<ObjectReference> references = endpoints.stream().map(Endpoints::getSubsets).filter(Objects::nonNull)
				.flatMap(List::stream).map(EndpointSubset::getAddresses).filter(Objects::nonNull).flatMap(List::stream)
				.map(EndpointAddress::getTargetRef);

		return Fabric8CatalogWatchContext.state(references);
	}

	private List<Endpoints> endpoints(Fabric8CatalogWatchContext context, String namespace, KubernetesClient client) {
		return client.endpoints().inNamespace(namespace).withLabels(context.properties().serviceLabels()).list()
			.getItems();
	}

}
