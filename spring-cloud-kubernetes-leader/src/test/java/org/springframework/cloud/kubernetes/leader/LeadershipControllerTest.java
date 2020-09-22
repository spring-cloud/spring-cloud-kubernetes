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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
public class LeadershipControllerTest {

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private LeaderEventPublisher mockLeaderEventPublisher;

	@Mock
	private KubernetesClient mockKubernetesClient;

	private LeadershipController leadershipController;

	@BeforeEach
	public void before() {
		this.leadershipController = new LeadershipController(this.mockCandidate, this.mockLeaderProperties,
				this.mockLeaderEventPublisher, this.mockKubernetesClient);
	}

	@Test
	public void shouldGetEmptyLocalLeader() {
		assertThat(this.leadershipController.getLocalLeader().isPresent()).isFalse();
	}

}
