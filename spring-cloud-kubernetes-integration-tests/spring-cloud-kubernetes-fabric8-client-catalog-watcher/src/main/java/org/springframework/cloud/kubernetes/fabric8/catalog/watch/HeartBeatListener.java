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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch;

import java.util.List;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Listener that will catch events from
 * {@link org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesCatalogWatch}
 *
 * @author wind57
 */
@Component
class HeartBeatListener implements ApplicationListener<ApplicationEvent> {

	private final EndpointNameAndNamespaceService service;

	HeartBeatListener(EndpointNameAndNamespaceService service) {
		this.service = service;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof HeartbeatEvent heartbeatEvent) {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) heartbeatEvent.getValue();
			service.setResult(result);
		}
	}

}
