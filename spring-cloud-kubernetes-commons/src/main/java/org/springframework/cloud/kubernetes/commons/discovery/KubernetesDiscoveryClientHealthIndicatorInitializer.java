/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.log.LogAccessor;

/**
 * @author Ryan Baxter
 */
public final class KubernetesDiscoveryClientHealthIndicatorInitializer {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesDiscoveryClientHealthIndicatorInitializer.class));

	private final PodUtils<?> podUtils;

	private final ApplicationEventPublisher applicationEventPublisher;

	public KubernetesDiscoveryClientHealthIndicatorInitializer(PodUtils<?> podUtils,
			ApplicationEventPublisher applicationEventPublisher) {
		this.podUtils = podUtils;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@PostConstruct
	private void postConstruct() {
		LOG.debug(() -> "publishing InstanceRegisteredEvent");
		InstanceRegisteredEvent<RegisteredEventSource> instanceRegisteredEvent = new InstanceRegisteredEvent<>(
				new RegisteredEventSource("kubernetes", podUtils.isInsideKubernetes(), podUtils.currentPod().get()),
				null);
		this.applicationEventPublisher.publishEvent(instanceRegisteredEvent);
	}

	/**
	 * @param cloudPlatform "kubernetes" always
	 * @param inside inside kubernetes or not
	 * @param pod an actual pod or null, if we are outside kubernetes
	 */
	public record RegisteredEventSource(String cloudPlatform, boolean inside, Object pod) {

	}

}
