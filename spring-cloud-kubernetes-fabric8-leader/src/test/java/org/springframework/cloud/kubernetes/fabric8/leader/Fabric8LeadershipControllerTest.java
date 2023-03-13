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

package org.springframework.cloud.kubernetes.fabric8.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
public class Fabric8LeadershipControllerTest {

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private LeaderEventPublisher mockLeaderEventPublisher;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private KubernetesClient mockKubernetesClient;

	private Fabric8LeadershipController fabric8LeadershipController;

	@BeforeEach
	public void before() {
		this.fabric8LeadershipController = new Fabric8LeadershipController(this.mockCandidate,
				this.mockLeaderProperties, this.mockLeaderEventPublisher, this.mockKubernetesClient);
	}

	@Test
	public void shouldGetEmptyLocalLeader() {
		assertThat(this.fabric8LeadershipController.getLocalLeader().isPresent()).isFalse();
	}

	@ExtendWith(OutputCaptureExtension.class)
	@Test
	void whenNonExistentConfigmapAndCreationNotAllowedStopLeadershipAcquire(CapturedOutput output) {
		// given
		String testNamespace = "test-namespace";
		String testConfigmap = "test-configmap";
		Resource mockResource = Mockito.mock(Resource.class);
		NonNamespaceOperation mockNonNamespaceOperation = Mockito.mock(NonNamespaceOperation.class);

		Fabric8LeadershipController fabric8LeadershipController = new Fabric8LeadershipController(mockCandidate,
				mockLeaderProperties, mockLeaderEventPublisher, mockKubernetesClient);

		when(mockLeaderProperties.isCreateConfigMap()).thenReturn(false);
		when(mockLeaderProperties.isPublishFailedEvents()).thenReturn(true);
		when(mockLeaderProperties.getConfigMapName()).thenReturn(testConfigmap);
		when(mockKubernetesClient.getNamespace()).thenReturn(testNamespace);
		when(mockLeaderProperties.getNamespace(anyString())).thenReturn(testNamespace);
		when(mockKubernetesClient.configMaps().inNamespace(anyString())).thenReturn(mockNonNamespaceOperation);
		when(mockNonNamespaceOperation.withName(any())).thenReturn(mockResource);
		when(mockResource.get()).thenReturn(null);

		// when
		fabric8LeadershipController.update();

		// then
		assertThat(output).contains("ConfigMap '" + testConfigmap + "' does not exist "
				+ "and leaderProperties.isCreateConfigMap() is false, cannot acquire leadership");
		verify(mockLeaderEventPublisher).publishOnFailedToAcquire(any(), any(), any());

		verify(mockKubernetesClient, never()).pods();
		verify(mockCandidate, never()).getId();
		verify(mockLeaderProperties, never()).getLeaderIdPrefix();
		verify(mockLeaderEventPublisher, never()).publishOnGranted(any(), any(), any());
		verify(mockLeaderEventPublisher, never()).publishOnRevoked(any(), any(), any());
	}

}
