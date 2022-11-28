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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.log.LogAccessor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * @author Oleg Vyukov
 */
public class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesCatalogWatch.class));

	private final KubernetesClient kubernetesClient;

	private final KubernetesDiscoveryProperties properties;

	private final KubernetesNamespaceProvider namespaceProvider;

	private volatile List<EndpointNameAndNamespace> catalogEndpointsState = null;

	private ApplicationEventPublisher publisher;

	public KubernetesCatalogWatch(KubernetesClient kubernetesClient, KubernetesDiscoveryProperties properties,
			KubernetesNamespaceProvider namespaceProvider) {
		this.kubernetesClient = kubernetesClient;
		this.properties = properties;
		this.namespaceProvider = namespaceProvider;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(fixedDelayString = "${spring.cloud.kubernetes.discovery.catalogServicesWatchDelay:30000}")
	public void catalogServicesWatch() {
		try {

			// not all pods participate in the service discovery. only those that have
			// endpoints.
			List<Endpoints> endpoints;
			if (properties.allNamespaces()) {
				LOG.debug(() -> "discovering endpoints in all namespaces");
				endpoints = kubernetesClient.endpoints().inAnyNamespace().withLabels(properties.serviceLabels()).list()
						.getItems();
			}
			else {
				String namespace = Fabric8Utils.getApplicationNamespace(kubernetesClient, null, "catalog-watcher",
						namespaceProvider);
				LOG.debug(() -> "fabric8 catalog watcher will use namespace : " + namespace);
				endpoints = kubernetesClient.endpoints().inNamespace(namespace).withLabels(properties.serviceLabels())
						.list().getItems();
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
			List<EndpointNameAndNamespace> currentState = endpoints.stream().map(Endpoints::getSubsets)
					.filter(Objects::nonNull).flatMap(List::stream).map(EndpointSubset::getAddresses)
					.filter(Objects::nonNull).flatMap(List::stream).map(EndpointAddress::getTargetRef)
					.filter(Objects::nonNull).map(x -> new EndpointNameAndNamespace(x.getName(), x.getNamespace()))
					.sorted(Comparator.comparing(EndpointNameAndNamespace::endpointName, String::compareTo)).toList();

			if (!currentState.equals(catalogEndpointsState)) {
				LOG.debug(() -> "Received endpoints update from kubernetesClient: " + currentState);
				publisher.publishEvent(new HeartbeatEvent(this, currentState));
			}

			catalogEndpointsState = currentState;
		}
		catch (Exception e) {
			LOG.error(e, () -> "Error watching Kubernetes Services");
		}
	}

}
