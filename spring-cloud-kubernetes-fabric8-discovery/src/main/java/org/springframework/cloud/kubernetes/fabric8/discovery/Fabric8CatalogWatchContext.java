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
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;

/**
 * A simple holder for some instances needed for either Endpoints or EndpointSlice catalog
 * implementations.
 *
 * @author wind57
 */
record Fabric8CatalogWatchContext(KubernetesClient kubernetesClient, KubernetesDiscoveryProperties properties,
		KubernetesNamespaceProvider namespaceProvider) {

	static List<EndpointNameAndNamespace> state(Stream<ObjectReference> references) {
		return references.filter(Objects::nonNull).map(x -> new EndpointNameAndNamespace(x.getName(), x.getNamespace()))
				.sorted(comparing(EndpointNameAndNamespace::endpointName, nullsLast(String::compareTo))).toList();
	}

}
