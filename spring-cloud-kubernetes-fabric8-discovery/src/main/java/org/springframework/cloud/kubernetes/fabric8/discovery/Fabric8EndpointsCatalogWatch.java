/*
 * Copyright 2012-present the original author or authors.
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
		KubernetesClient kubernetesClient = context.kubernetesClient();
		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			endpoints = endpoints(kubernetesClient, context.properties().serviceLabels());
		}
		else if (!context.properties().namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in " + context.properties().namespaces());
			List<Endpoints> inner = new ArrayList<>(context.properties().namespaces().size());
			context.properties()
				.namespaces()
				.forEach(namespace -> inner
					.addAll(namespacedEndpoints(kubernetesClient, namespace, context.properties().serviceLabels())));
			endpoints = inner;
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(kubernetesClient, null, "fabric8 discovery",
					context.namespaceProvider());
			LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
			endpoints = namespacedEndpoints(kubernetesClient, namespace, context.properties().serviceLabels());
		}

		return generateState(endpoints);
	}

	/**
	 * This one is visible for testing, especially since fabric8 mock client will save
	 * null subsets as empty lists, thus blocking some unit test.
	 *
	 * <pre>
	 *   - An "Endpoints" holds a List of EndpointSubset.
	 *   - A single EndpointSubset holds a List of EndpointAddress
	 *
	 *   - (The union of all EndpointSubsets is the Set of all Endpoints)
	 *   - Set of Endpoints is the cartesian product of :
	 *     EndpointSubset::getAddresses and EndpointSubset::getPorts (each is a List)
	 * </pre>
	 */
	List<EndpointNameAndNamespace> generateState(List<Endpoints> endpoints) {
		Stream<ObjectReference> references = endpoints.stream()
			.map(Endpoints::getSubsets)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.map(EndpointSubset::getAddresses)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.map(EndpointAddress::getTargetRef);

		return Fabric8CatalogWatchContext.state(references);
	}

	private List<Endpoints> endpoints(KubernetesClient client, Map<String, String> labels) {
		return client.endpoints().inAnyNamespace().withLabels(labels).list().getItems();
	}

	private List<Endpoints> namespacedEndpoints(KubernetesClient kubernetesClient, String namespace,
			Map<String, String> labels) {
		return kubernetesClient.endpoints().inNamespace(namespace).withLabels(labels).list().getItems();
	}

}
