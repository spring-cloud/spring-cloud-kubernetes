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

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.ALWAYS_TRUE;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.endpoints;

/**
 * Implementation that is based on Endpoints.
 *
 * @author wind57
 */
final class Fabric8EndpointsCatalogWatch
		implements Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> {

	@Override
	public List<EndpointNameAndNamespace> apply(Fabric8CatalogWatchContext context) {
		List<Endpoints> endpoints = endpoints(context.properties(), context.kubernetesClient(),
				context.namespaceProvider(), "catalog-watcher", null, ALWAYS_TRUE);

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

}
