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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;

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

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> mockConfigMapsOperation;

	@Mock
	private Resource<ConfigMap, DoneableConfigMap> mockConfigMapResource;

	@Mock
	private MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> mockPodsOperation;

	@Mock
	private PodResource<Pod, DoneablePod> mockPodResource;

	@Mock
	private ConfigMap mockConfigMap;

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

	@Test
	public void shouldHandleExistingLeaderPodNull() {
		initStubs();
		String podName = "mysvc-nonexistent-pod";

		Map<String, String> data = new HashMap<>();
		data.put("mysvcrole", podName);

		given(this.mockConfigMap.getData()).willReturn(data);

		given(this.mockPodsOperation.withName(podName)).willReturn(this.mockPodResource);
		// io.fabric8.kubernetes.client.internal.readiness.Readiness::isReady()#L62
		given(this.mockPodResource.isReady())
			.willThrow(
				new IllegalArgumentException("Item needs to be one of [Node, Deployment, ReplicaSet,.."))
			.willReturn(Boolean.FALSE);

		given(this.mockPodsOperation.withName("myid")).willReturn(this.mockPodResource);
		this.fabric8LeadershipController.update();
	}

	private void initStubs() {
		given(this.mockKubernetesClient.getNamespace()).willReturn("ns");
		given(this.mockLeaderProperties.getNamespace("ns")).willReturn("ns");
		given(this.mockLeaderProperties.getConfigMapName()).willReturn("leader");
		given(this.mockLeaderProperties.getLeaderIdPrefix()).willReturn("mysvc");

		given(this.mockCandidate.getId()).willReturn("myid");
		given(this.mockCandidate.getRole()).willReturn("role");

		given(this.mockKubernetesClient.configMaps()).willReturn(this.mockConfigMapsOperation);
		given(this.mockConfigMapsOperation.inNamespace("ns")).willReturn(mockConfigMapsOperation);
		given(this.mockConfigMapsOperation.withName("leader")).willReturn(mockConfigMapResource);

		given(this.mockConfigMapResource.get()).willReturn(mockConfigMap);

		given(this.mockKubernetesClient.pods()).willReturn(this.mockPodsOperation);
	}

}
