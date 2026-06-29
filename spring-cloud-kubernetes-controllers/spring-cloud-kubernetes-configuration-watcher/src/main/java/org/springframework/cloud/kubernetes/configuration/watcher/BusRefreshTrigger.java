/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher;

import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.bus.event.PathDestinationFactory;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.event.ShutdownRemoteApplicationEvent;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.RefreshStrategy.SHUTDOWN;
import static org.springframework.cloud.kubernetes.configuration.watcher.WatcherUtil.matchesByLabels;

/**
 * An event publisher for an 'event bus' type of application.
 *
 * @author wind57
 */
final class BusRefreshTrigger implements RefreshTrigger {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(BusRefreshTrigger.class));

	private final ApplicationEventPublisher applicationEventPublisher;

	private final String busId;

	private final ConfigurationWatcherConfigurationProperties watcherConfigurationProperties;

	private final ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider;

	BusRefreshTrigger(ApplicationEventPublisher applicationEventPublisher, String busId,
			ConfigurationWatcherConfigurationProperties watcherConfigurationProperties,
			ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.busId = busId;
		this.watcherConfigurationProperties = watcherConfigurationProperties;
		this.reactiveDiscoveryClientProvider = reactiveDiscoveryClientProvider;
	}

	/**
	 * Publish bus refresh events for services selected either by explicit service names
	 * or by labels.
	 *
	 * <p>
	 * When service names are used, we publish one bus event for each configured service
	 * name.
	 *
	 * <p>
	 * When labels are used, we first discover service instances because the labels are
	 * matched against {@link ServiceInstance#getMetadata()}. Once matching instances are
	 * found, we reduce them to distinct service ids and publish one bus event per
	 * service.
	 *
	 * <p>
	 * This class does not call pods directly. It publishes bus events, and the matching
	 * application instances receive those events from Spring Cloud Bus and refresh
	 * themselves.
	 */
	@Override
	public Mono<Void> triggerRefresh(KubernetesSource kubernetesSource) {
		if (!kubernetesSource.serviceLabels().isEmpty()) {
			LOG.info(() -> "Using service labels for discovery : " + kubernetesSource.serviceLabels());
			ReactiveDiscoveryClient reactiveDiscoveryClient = reactiveDiscoveryClientProvider.getIfAvailable();
			if (reactiveDiscoveryClient == null) {
				throw new IllegalStateException("Using spring.cloud.kubernetes.configmap.labels or "
						+ "spring.cloud.kubernetes.secret.labels with bus refresh requires a ReactiveDiscoveryClient");
			}
			reactiveDiscoveryClient.getServices()
				.flatMap(reactiveDiscoveryClient::getInstances)
				.filter(serviceInstance -> matchesByLabels(serviceInstance, kubernetesSource.serviceLabels()))
				.map(ServiceInstance::getServiceId)
				.distinct()
				.toIterable()
				.forEach(serviceName -> publishRefreshEvent(kubernetesSource, serviceName));
			return Mono.empty();
		}

		LOG.info(() -> "Using service names for discovery : " + kubernetesSource.serviceNames());
		kubernetesSource.serviceNames().forEach(serviceName -> publishRefreshEvent(kubernetesSource, serviceName));
		return Mono.empty();
	}

	private void publishRefreshEvent(KubernetesSource kubernetesSource, String serviceName) {
		LOG.debug(() -> "Publishing refresh event for " + serviceName + " using " + kubernetesSource.description());
		applicationEventPublisher.publishEvent(createRefreshApplicationEvent(kubernetesSource, serviceName));
	}

	private RemoteApplicationEvent createRefreshApplicationEvent(KubernetesSource kubernetesSource, String appName) {
		if (watcherConfigurationProperties.getRefreshStrategy() == SHUTDOWN) {
			return new ShutdownRemoteApplicationEvent(kubernetesSource, busId,
					new PathDestinationFactory().getDestination(appName));
		}
		return new RefreshRemoteApplicationEvent(kubernetesSource, busId,
				new PathDestinationFactory().getDestination(appName));
	}

}
