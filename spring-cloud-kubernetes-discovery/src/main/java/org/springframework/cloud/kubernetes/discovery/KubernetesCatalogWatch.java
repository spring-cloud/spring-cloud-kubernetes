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

package org.springframework.cloud.kubernetes.discovery;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.LogFactory;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.log.LogAccessor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.CATALOG_WATCH_PROPERTY_WITH_DEFAULT_VALUE;

/**
 * @author wind57
 */
final class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final ParameterizedTypeReference<List<EndpointNameAndNamespace>> TYPE = new ParameterizedTypeReference<>() {

	};

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesCatalogWatch.class));

	private final AtomicReference<List<EndpointNameAndNamespace>> catalogState = new AtomicReference<>(List.of());

	private final RestTemplate restTemplate;

	private ApplicationEventPublisher publisher;

	KubernetesCatalogWatch(RestTemplateBuilder builder, KubernetesDiscoveryProperties properties) {
		this.restTemplate = builder.rootUri(properties.discoveryServerUrl()).build();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(fixedDelayString = "${" + CATALOG_WATCH_PROPERTY_WITH_DEFAULT_VALUE + "}")
	public void catalogServicesWatch() {
		try {
			List<EndpointNameAndNamespace> currentState = restTemplate.exchange("/state", HttpMethod.GET, null, TYPE)
					.getBody();

			if (!catalogState.get().equals(currentState)) {
				LOG.debug(() -> "Received update from kubernetes discovery http client: " + currentState);
				publisher.publishEvent(new HeartbeatEvent(this, currentState));
			}

			catalogState.set(currentState);
		}
		catch (Exception e) {
			LOG.error(e, () -> "Error watching Kubernetes Services");
		}
	}

}
