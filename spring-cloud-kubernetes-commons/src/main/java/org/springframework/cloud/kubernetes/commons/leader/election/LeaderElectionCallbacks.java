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

package org.springframework.cloud.kubernetes.commons.leader.election;

import java.net.UnknownHostException;
import java.util.function.Consumer;

import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.cloud.kubernetes.commons.leader.election.events.NewLeaderEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;

/**
 * common leader election callbacks that are supposed to be used in both fabric8 and
 * k8s-native clients.
 *
 * @author wind57
 */
public class LeaderElectionCallbacks {

	private static final LogAccessor LOG = new LogAccessor(LeaderElectionCallbacks.class);

	@Bean
	public final String holderIdentity() throws UnknownHostException {
		String podHostName = LeaderUtils.hostName();
		LOG.debug(() -> "using pod hostname : " + podHostName);
		return podHostName;
	}

	@Bean
	public final String podNamespace() {
		String podNamespace = LeaderUtils.podNamespace().orElse("default");
		LOG.debug(() -> "using pod namespace : " + podNamespace);
		return podNamespace;
	}

	@Bean
	public final Runnable onStartLeadingCallback(ApplicationEventPublisher applicationEventPublisher,
			String holderIdentity, LeaderElectionProperties properties) {
		return () -> {
			LOG.info(() -> holderIdentity + " is now a leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new StartLeadingEvent(holderIdentity));
			}
		};
	}

	@Bean
	public final Runnable onStopLeadingCallback(ApplicationEventPublisher applicationEventPublisher,
			String holderIdentity, LeaderElectionProperties properties) {
		return () -> {
			LOG.info(() -> "id : " + holderIdentity + " stopped being a leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new StopLeadingEvent(holderIdentity));
			}
		};
	}

	@Bean
	public final Consumer<String> onNewLeaderCallback(ApplicationEventPublisher applicationEventPublisher,
			LeaderElectionProperties properties) {
		return holderIdentity -> {
			LOG.info(() -> "id : " + holderIdentity + " is the new leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new NewLeaderEvent(holderIdentity));
			}
		};
	}

}
