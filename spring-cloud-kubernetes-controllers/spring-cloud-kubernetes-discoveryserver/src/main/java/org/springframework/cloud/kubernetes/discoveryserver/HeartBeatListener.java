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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnHttpDiscoveryCatalogWatcherEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesCatalogEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;
import org.springframework.stereotype.Component;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.CATALOG_WATCHER_DEFAULT_DELAY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.CATALOG_WATCH_PROPERTY_NAME;

/**
 * Listener for a HeartbeatEvent that comes from KubernetesCatalogWatch.
 *
 * @author wind57
 */
@Component
@ConditionalOnKubernetesCatalogEnabled
@ConditionalOnHttpDiscoveryCatalogWatcherEnabled
class HeartBeatListener implements ApplicationListener<HeartbeatEvent> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(HeartBeatListener.class));

	private final AtomicReference<List<EndpointNameAndNamespace>> lastState = new AtomicReference<>(List.of());

	HeartBeatListener(Environment environment) {
		String watchDelay = environment.getProperty(CATALOG_WATCH_PROPERTY_NAME);
		if (watchDelay != null) {
			LOG.debug("using delay : " + watchDelay);
		}
		else {
			LOG.debug("using default watch delay : " + CATALOG_WATCHER_DEFAULT_DELAY);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onApplicationEvent(HeartbeatEvent event) {
		LOG.debug(() -> "received heartbeat event");
		List<EndpointNameAndNamespace> state = (List<EndpointNameAndNamespace>) event.getValue();
		LOG.debug(() -> "state received : " + state);
		lastState.set(state);
	}

	AtomicReference<List<EndpointNameAndNamespace>> lastState() {
		return lastState;
	}

}
