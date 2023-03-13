/*
 * Copyright 2013-2023 the original author or authors.
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

import java.util.List;
import java.util.function.Function;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.log.LogAccessor;
import org.springframework.scheduling.annotation.Scheduled;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.DISCOVERY_GROUP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.DISCOVERY_VERSION;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.ENDPOINT_SLICE;

/**
 * Catalog watch implementation for kubernetes native client.
 *
 * @author wind57
 */
class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesCatalogWatch.class));

	private final KubernetesCatalogWatchContext context;

	private Function<KubernetesCatalogWatchContext, List<EndpointNameAndNamespace>> stateGenerator;

	private volatile List<EndpointNameAndNamespace> catalogEndpointsState = null;

	private ApplicationEventPublisher publisher;

	KubernetesCatalogWatch(CoreV1Api coreV1Api, ApiClient apiClient, KubernetesDiscoveryProperties properties,
			KubernetesNamespaceProvider namespaceProvider) {
		context = new KubernetesCatalogWatchContext(coreV1Api, apiClient, properties, namespaceProvider);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(fixedDelayString = "${spring.cloud.kubernetes.discovery.catalogServicesWatchDelay:30000}")
	void catalogServicesWatch() {
		try {

			List<EndpointNameAndNamespace> currentState = stateGenerator.apply(context);

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

	@PostConstruct
	void postConstruct() {
		stateGenerator = stateGenerator();
	}

	Function<KubernetesCatalogWatchContext, List<EndpointNameAndNamespace>> stateGenerator() {

		Function<KubernetesCatalogWatchContext, List<EndpointNameAndNamespace>> localStateGenerator;

		if (context.properties().useEndpointSlices()) {
			// this emulates : 'kubectl api-resources | grep -i EndpointSlice'
			ApiClient apiClient = context.apiClient();
			CustomObjectsApi customObjectsApi = new CustomObjectsApi(apiClient);
			try {
				List<V1APIResource> resources = customObjectsApi.getAPIResources(DISCOVERY_GROUP, DISCOVERY_VERSION)
						.getResources();
				boolean found = resources.stream().map(V1APIResource::getKind).anyMatch(ENDPOINT_SLICE::equals);
				if (!found) {
					throw new IllegalArgumentException("EndpointSlices are not supported on the cluster");
				}
				else {
					localStateGenerator = new KubernetesEndpointSlicesCatalogWatch();
				}
			}
			catch (ApiException e) {
				throw new RuntimeException(e);
			}

		}
		else {
			localStateGenerator = new KubernetesEndpointsCatalogWatch();
		}

		LOG.debug(() -> "stateGenerator is of type: " + localStateGenerator.getClass().getSimpleName());

		return localStateGenerator;
	}

}
