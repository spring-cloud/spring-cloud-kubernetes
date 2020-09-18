/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.leader;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
public class LeaderAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(LeaderEventPublisher.class)
	public LeaderEventPublisher defaultLeaderEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new DefaultLeaderEventPublisher(applicationEventPublisher);
	}

	@Bean
	public Candidate candidate(LeaderProperties leaderProperties) throws UnknownHostException {
		String id = Inet4Address.getLocalHost().getHostName();
		String role = leaderProperties.getRole();

		return new DefaultCandidate(id, role);
	}

	@Bean
	public LeadershipController leadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher, KubernetesClient kubernetesClient) {
		return new LeadershipController(candidate, leaderProperties, leaderEventPublisher, kubernetesClient);
	}

	@Bean
	public LeaderRecordWatcher leaderRecordWatcher(LeaderProperties leaderProperties,
			LeadershipController leadershipController, KubernetesClient kubernetesClient) {
		return new LeaderRecordWatcher(leaderProperties, leadershipController, kubernetesClient);
	}

	@Bean
	public PodReadinessWatcher hostPodWatcher(Candidate candidate, KubernetesClient kubernetesClient,
			LeadershipController leadershipController) {
		return new PodReadinessWatcher(candidate.getId(), kubernetesClient, leadershipController);
	}

	@Bean(destroyMethod = "stop")
	public LeaderInitiator leaderInitiator(LeaderProperties leaderProperties, LeadershipController leadershipController,
			LeaderRecordWatcher leaderRecordWatcher, PodReadinessWatcher hostPodWatcher) {
		return new LeaderInitiator(leaderProperties, leadershipController, leaderRecordWatcher, hostPodWatcher);
	}

	@Bean
	@ConditionalOnClass(InfoContributor.class)
	public LeaderInfoContributor leaderInfoContributor(LeadershipController leadershipController, Candidate candidate) {
		return new LeaderInfoContributor(leadershipController, candidate);
	}

}
