/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * @author Oleg Vyukov
 */
public class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final Logger logger = LoggerFactory.getLogger(KubernetesCatalogWatch.class);

	private final KubernetesClient kubernetesClient;
	private final AtomicReference<List<String>> catalogEndpointsState = new AtomicReference<>();
	private ApplicationEventPublisher publisher;


	public KubernetesCatalogWatch(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(fixedDelayString = "${spring.cloud.kubernetes.discovery.catalogServicesWatchDelay:30000}")
	public void catalogServicesWatch() {
		try {
			List<String> previousState = catalogEndpointsState.get();

			//not all pods participate in the service discovery. only those that have endpoints.
			List<Endpoints> endpoints = kubernetesClient.endpoints().list().getItems();
			List<String> endpointsPodNames =
				endpoints.stream()
					.flatMap(endpoint -> endpoint.getSubsets().stream())
					.flatMap(subset -> subset.getAddresses().stream())
					.map(endpointAddress -> endpointAddress.getTargetRef().getName()) // pod name unique in namespace
					.sorted(String::compareTo).collect(Collectors.toList());

			catalogEndpointsState.set(endpointsPodNames);

			if (!endpointsPodNames.equals(previousState)) {
				logger.trace("Received endpoints update from kubernetesClient: {}", endpointsPodNames);
				publisher.publishEvent(new HeartbeatEvent(this, endpointsPodNames));
			}
		} catch (Exception e) {
			logger.error("Error watching Kubernetes Services", e);
		}
	}


}
