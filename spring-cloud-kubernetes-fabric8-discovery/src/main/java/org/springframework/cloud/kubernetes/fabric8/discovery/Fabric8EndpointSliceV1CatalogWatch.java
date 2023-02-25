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
import java.util.function.Function;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.endpointSlices;

/**
 * Implementation that is based on EndpointSlice V1.
 *
 * @author wind57
 */
final class Fabric8EndpointSliceV1CatalogWatch
		implements Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> {

	@Override
	public List<EndpointNameAndNamespace> apply(Fabric8CatalogWatchContext context) {
		List<EndpointSlice> endpointSlices = endpointSlices(context.properties(), context.kubernetesClient(),
			context.namespaceProvider(), "catalog-watcher");

		Stream<ObjectReference> references = endpointSlices.stream().map(EndpointSlice::getEndpoints)
				.flatMap(List::stream).map(Endpoint::getTargetRef);

		return Fabric8CatalogWatchContext.state(references);

	}

}
