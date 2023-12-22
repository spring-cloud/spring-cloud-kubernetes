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

package org.springframework.cloud.kubernetes.discoveryclient.it;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.context.ApplicationListener;
import org.springframework.core.log.LogAccessor;
import org.springframework.stereotype.Component;

/**
 * @author wind57
 */
@Component
class HeartbeatListener implements ApplicationListener<HeartbeatEvent> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(HeartbeatListener.class));

	AtomicReference<List<EndpointNameAndNamespace>> state = new AtomicReference<>(List.of());

	@Override
	@SuppressWarnings("unchecked")
	public void onApplicationEvent(HeartbeatEvent event) {
		LOG.info("received heartbeat event");
		List<EndpointNameAndNamespace> state = (List<EndpointNameAndNamespace>) event.getValue();
		this.state.set(state);
		LOG.info("state received : " + state);
	}

}
