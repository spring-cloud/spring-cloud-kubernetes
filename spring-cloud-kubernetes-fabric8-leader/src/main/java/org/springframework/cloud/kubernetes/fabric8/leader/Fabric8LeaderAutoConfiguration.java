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

package org.springframework.cloud.kubernetes.fabric8.leader;

import java.net.UnknownHostException;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeaderInfoContributor;
import org.springframework.cloud.kubernetes.commons.leader.LeaderInitiator;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 * @author Gytis Trikleris
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderProperties.class)
@ConditionalOnBean(KubernetesClient.class)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.leader.enabled", matchIfMissing = true)
public class Fabric8LeaderAutoConfiguration {

	/*
	 * Used for publishing application events that happen: granted, revoked or failed to
	 * acquire mutex.
	 */
	@Bean
	@ConditionalOnMissingBean(LeaderEventPublisher.class)
	public LeaderEventPublisher defaultLeaderEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new DefaultLeaderEventPublisher(applicationEventPublisher);
	}

	/*
	 * This can be thought as "self" or the pod that participates in leader election
	 * process. The implementation that we return simply logs events that happen during
	 * that process.
	 */
	@Bean
	public Candidate candidate(LeaderProperties leaderProperties) throws UnknownHostException {
		String id = LeaderUtils.hostName();
		String role = leaderProperties.getRole();
		return new DefaultCandidate(id, role);
	}

	/*
	 * Add an info contributor with leader information.
	 */
	@Bean
	@ConditionalOnClass(InfoContributor.class)
	public LeaderInfoContributor leaderInfoContributor(Fabric8LeadershipController fabric8LeadershipController,
			Candidate candidate) {
		return new LeaderInfoContributor(fabric8LeadershipController, candidate);
	}

	/**
	 * watches the readiness of the pod. In case of a readiness change, it has to go
	 * through leader process again.
	 */
	@Bean
	public Fabric8PodReadinessWatcher hostPodWatcher(Candidate candidate, KubernetesClient kubernetesClient,
			Fabric8LeadershipController fabric8LeadershipController) {
		return new Fabric8PodReadinessWatcher(candidate.getId(), kubernetesClient, fabric8LeadershipController);
	}

	@Bean
	public Fabric8LeadershipController leadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher, KubernetesClient kubernetesClient) {
		return new Fabric8LeadershipController(candidate, leaderProperties, leaderEventPublisher, kubernetesClient);
	}

	@Bean
	public Fabric8LeaderRecordWatcher leaderRecordWatcher(LeaderProperties leaderProperties,
			Fabric8LeadershipController fabric8LeadershipController, KubernetesClient kubernetesClient) {
		return new Fabric8LeaderRecordWatcher(leaderProperties, fabric8LeadershipController, kubernetesClient);
	}

	@Bean(destroyMethod = "stop")
	public LeaderInitiator leaderInitiator(LeaderProperties leaderProperties,
			Fabric8LeadershipController fabric8LeadershipController,
			Fabric8LeaderRecordWatcher fabric8LeaderRecordWatcher, Fabric8PodReadinessWatcher hostPodWatcher) {
		return new LeaderInitiator(leaderProperties, fabric8LeadershipController, fabric8LeaderRecordWatcher,
				hostPodWatcher);
	}

}
