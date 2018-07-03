/*
 * Copyright (C) 2018 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@Configuration
@EnableConfigurationProperties(LeaderProperties.class)
@ConditionalOnBean(KubernetesClient.class)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.leader.enabled", matchIfMissing = true)
public class LeaderAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(LeaderEventPublisher.class)
	LeaderEventPublisher defaultLeaderEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new DefaultLeaderEventPublisher(applicationEventPublisher);
	}

	@Bean
	LeaderKubernetesHelper leaderKubernetesHelper(LeaderProperties leaderProperties,
		KubernetesClient kubernetesClient) {
		return new LeaderKubernetesHelper(leaderProperties, kubernetesClient);
	}

	@Bean
	LeadershipController leadershipController(LeaderProperties leaderProperties,
		LeaderKubernetesHelper kubernetesHelper, LeaderEventPublisher leaderEventPublisher) {
		return new LeadershipController(leaderProperties, kubernetesHelper, leaderEventPublisher);
	}

	@Bean(destroyMethod = "stop")
	public LeaderInitiator leaderInitiator(LeadershipController leadershipController, LeaderProperties leaderProperties)
		throws UnknownHostException {
		Candidate candidate = getCandidate(leaderProperties);
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

		return new LeaderInitiator(leaderProperties, leadershipController, candidate, scheduledExecutorService);
	}

	private Candidate getCandidate(LeaderProperties leaderProperties) throws UnknownHostException {
		String id = Inet4Address.getLocalHost().getHostName();
		String role = leaderProperties.getRole();

		return new DefaultCandidate(id, role);
	}

}
