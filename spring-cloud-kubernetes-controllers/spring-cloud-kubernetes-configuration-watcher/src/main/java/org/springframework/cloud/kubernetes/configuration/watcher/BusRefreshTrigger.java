package org.springframework.cloud.kubernetes.configuration.watcher;

import io.kubernetes.client.common.KubernetesObject;
import org.springframework.cloud.bus.event.PathDestinationFactory;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

/**
 * An event publisher for an 'event bus' type of application
 *
 * @author wind57
 */
final class BusRefreshTrigger implements RefreshTrigger {

	private final ApplicationEventPublisher applicationEventPublisher;

	private final String busId;

	public BusRefreshTrigger(ApplicationEventPublisher applicationEventPublisher, String busId) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.busId = busId;
	}

	@Override
	public Mono<Void> triggerRefresh(KubernetesObject configMap) {
		applicationEventPublisher.publishEvent(new RefreshRemoteApplicationEvent(configMap, busId,
			new PathDestinationFactory().getDestination(configMap.getMetadata().getName())));
		return Mono.empty();
	}
}
