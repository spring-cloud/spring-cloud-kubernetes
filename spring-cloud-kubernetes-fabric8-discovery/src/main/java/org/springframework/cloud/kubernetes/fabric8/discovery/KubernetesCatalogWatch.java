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
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.api.model.APIResourceList;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscovery;
import io.fabric8.kubernetes.client.KubernetesClient;
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
 * @author Oleg Vyukov
 */
public class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final String DISCOVERY_GROUP_VERSION = DISCOVERY_GROUP + "/" + DISCOVERY_VERSION;

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesCatalogWatch.class));

	private final Fabric8CatalogWatchContext context;

	private Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> stateGenerator;

	private volatile List<EndpointNameAndNamespace> catalogEndpointsState = null;

	private ApplicationEventPublisher publisher;

	public KubernetesCatalogWatch(KubernetesClient kubernetesClient, KubernetesDiscoveryProperties properties,
			KubernetesNamespaceProvider namespaceProvider) {
		context = new Fabric8CatalogWatchContext(kubernetesClient, properties, namespaceProvider);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(fixedDelayString = "${spring.cloud.kubernetes.discovery.catalogServicesWatchDelay:30000}")
	public void catalogServicesWatch() {
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

	Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> stateGenerator() {

		Function<Fabric8CatalogWatchContext, List<EndpointNameAndNamespace>> localStateGenerator;

		if (context.properties().useEndpointSlices()) {
			// can't use try with resources here as it will close the client
			KubernetesClient client = context.kubernetesClient();
			// this emulates : 'kubectl api-resources | grep -i EndpointSlice'
			boolean found = client.getApiGroups().getGroups().stream().flatMap(x -> x.getVersions().stream())
					.map(GroupVersionForDiscovery::getGroupVersion).filter(DISCOVERY_GROUP_VERSION::equals).findFirst()
					.map(client::getApiResources).map(APIResourceList::getResources)
					.map(x -> x.stream().map(APIResource::getKind))
					.flatMap(x -> x.filter(y -> y.equals(ENDPOINT_SLICE)).findFirst()).isPresent();

			if (!found) {
				throw new IllegalArgumentException("EndpointSlices are not supported on the cluster");
			}
			else {
				localStateGenerator = new Fabric8EndpointSliceV1CatalogWatch();
			}
		}
		else {
			localStateGenerator = new Fabric8EndpointsCatalogWatch();
		}

		LOG.debug(() -> "stateGenerator is of type: " + localStateGenerator.getClass().getSimpleName());

		return localStateGenerator;
	}

}
