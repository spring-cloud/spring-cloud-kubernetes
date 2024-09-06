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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

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
public class Fabric8LeadershipControllerTest {

	private final Candidate mockCandidate = Mockito.mock(Candidate.class);

	private final LeaderProperties mockLeaderProperties = Mockito.mock(LeaderProperties.class);

	private final LeaderEventPublisher mockLeaderEventPublisher = Mockito.mock(LeaderEventPublisher.class);

	private final KubernetesClient mockKubernetesClient = Mockito.mock(KubernetesClient.class,
			Mockito.RETURNS_DEEP_STUBS);

	private Fabric8LeadershipController fabric8LeadershipController;

	@BeforeEach
	void beforeEach() {
		fabric8LeadershipController = new Fabric8LeadershipController(mockCandidate, mockLeaderProperties,
				mockLeaderEventPublisher, mockKubernetesClient);
	}

	@Test
	void shouldGetEmptyLocalLeader() {
		assertThat(fabric8LeadershipController.getLocalLeader().isPresent()).isFalse();
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void whenNonExistentConfigmapAndCreationNotAllowedStopLeadershipAcquire(CapturedOutput output) {
		// given
		String testNamespace = "test-namespace";
		String testConfigmap = "test-configmap";
		@SuppressWarnings("unchecked")
		Resource<ConfigMap> mockResource = Mockito.mock(Resource.class);
		@SuppressWarnings("unchecked")
		NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockNonNamespaceOperation = Mockito
			.mock(NonNamespaceOperation.class);

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
		assertThat(output).contains(
				"ConfigMap 'test-configmap' does not exist and leaderProperties.isCreateConfigMap() is false, cannot acquire leadership");
		verify(mockLeaderEventPublisher).publishOnFailedToAcquire(any(), any(), any());

		verify(mockKubernetesClient, never()).pods();
		verify(mockCandidate, never()).getId();
		verify(mockLeaderProperties, never()).getLeaderIdPrefix();
		verify(mockLeaderEventPublisher, never()).publishOnGranted(any(), any(), any());
		verify(mockLeaderEventPublisher, never()).publishOnRevoked(any(), any(), any());
	}

}
