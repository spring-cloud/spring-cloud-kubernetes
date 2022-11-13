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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
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

	private volatile List<String> catalogEndpointsState = null;

	private ApplicationEventPublisher publisher;

	public KubernetesCatalogWatch(KubernetesClient kubernetesClient, KubernetesDiscoveryProperties properties) {
		this.kubernetesClient = kubernetesClient;
		this.properties = properties;
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
				endpoints = kubernetesClient.endpoints().inAnyNamespace().withLabels(properties.serviceLabels()).list()
						.getItems();
			}
			else {
				endpoints = kubernetesClient.endpoints().withLabels(properties.serviceLabels()).list().getItems();
			}

			List<String> endpointsPodNames = endpoints.stream().map(Endpoints::getSubsets).filter(Objects::nonNull)
					.flatMap(List::stream).map(EndpointSubset::getAddresses).filter(Objects::nonNull)
					.flatMap(List::stream).map(EndpointAddress::getTargetRef).filter(Objects::nonNull)
					.map(ObjectReference::getName).sorted(String::compareTo).collect(Collectors.toList());

			if (!endpointsPodNames.equals(catalogEndpointsState)) {
				LOG.debug(() -> "Received endpoints update from kubernetesClient: " + endpointsPodNames);
				this.publisher.publishEvent(new HeartbeatEvent(this, endpointsPodNames));
			}

			catalogEndpointsState = endpointsPodNames;
		}
		catch (Exception e) {
			LOG.error(e, () -> "Error watching Kubernetes Services");
		}
	}

}
