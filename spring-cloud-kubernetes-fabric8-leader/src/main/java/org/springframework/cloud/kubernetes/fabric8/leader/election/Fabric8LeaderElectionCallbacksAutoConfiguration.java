/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import java.net.UnknownHostException;
import java.util.function.Consumer;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.cloud.kubernetes.commons.leader.election.ConditionalOnLeaderElectionEnabled;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.cloud.kubernetes.commons.leader.election.events.NewLeaderEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StartLeadingEvent;
import org.springframework.cloud.kubernetes.commons.leader.election.events.StopLeadingEvent;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderElectionProperties.class)
@ConditionalOnBean(KubernetesClient.class)
@ConditionalOnLeaderElectionEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureAfter({ Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class })
class Fabric8LeaderElectionCallbacksAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(Fabric8LeaderElectionCallbacksAutoConfiguration.class);

	@Bean
	String holderIdentity() throws UnknownHostException {
		String podHostName = LeaderUtils.hostName();
		LOG.debug(() -> "using pod hostname : " + podHostName);
		return podHostName;
	}

	@Bean
	String podNamespace() {
		 String podNamespace = LeaderUtils.podNamespace().orElse("default");
		 LOG.debug(() -> "using pod namespace : " + podNamespace);
		 return podNamespace;
	}

	@Bean
	Runnable onStartLeadingCallback(ApplicationEventPublisher applicationEventPublisher, String holderIdentity,
			LeaderElectionProperties properties) {
		return () -> {
			LOG.info(() -> "id : " + holderIdentity + " is now a leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new StartLeadingEvent(holderIdentity));
			}
		};
	}

	@Bean
	Runnable onStopLeadingCallback(ApplicationEventPublisher applicationEventPublisher, String holderIdentity,
			LeaderElectionProperties properties) {
		return () -> {
			LOG.info(() -> "id : " + holderIdentity + " stopped being a leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new StopLeadingEvent(holderIdentity));
			}
		};
	}

	@Bean
	Consumer<String> onNewLeaderCallback(ApplicationEventPublisher applicationEventPublisher,
			LeaderElectionProperties properties) {
		return holderIdentity -> {
			LOG.info(() -> "id : " + holderIdentity + " is the new leader");
			if (properties.publishEvents()) {
				applicationEventPublisher.publishEvent(new NewLeaderEvent(holderIdentity));
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean
	Fabric8LeaderElectionCallbacks fabric8LeaderElectionCallbacks(Runnable onStartLeadingCallback,
			Runnable onStopLeadingCallback, Consumer<String> onNewLeaderCallback) {
		return new Fabric8LeaderElectionCallbacks(onStartLeadingCallback, onStopLeadingCallback, onNewLeaderCallback);
	}

}
