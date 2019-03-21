/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
@RunWith(MockitoJUnitRunner.class)
public class PodReadinessWatcherTest {

	private static final String POD_NAME = "test-pod";

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> mockPodsOperation;

	@Mock
	private PodResource<Pod, DoneablePod> mockPodResource;

	@Mock
	private Pod mockPod;

	@Mock
	private PodStatus mockPodStatus;

	@Mock
	private Watch mockWatch;

	@Mock
	private KubernetesClientException mockKubernetesClientException;

	private PodReadinessWatcher watcher;

	@Before
	public void before() {
		watcher = new PodReadinessWatcher(POD_NAME, mockKubernetesClient, mockLeadershipController);

		given(mockKubernetesClient.pods()).willReturn(mockPodsOperation);
		given(mockPodsOperation.withName(POD_NAME)).willReturn(mockPodResource);
		given(mockPodResource.watch(watcher)).willReturn(mockWatch);
	}

	@Test
	public void shouldStartOnce() {
		watcher.start();
		watcher.start();

		verify(mockPodResource).watch(watcher);
	}

	@Test
	public void shouldStopOnce() {
		watcher.start();
		watcher.stop();
		watcher.stop();

		verify(mockWatch).close();
	}

	@Test
	public void shouldHandleEventWithStateChange() {
		given(mockPodResource.isReady()).willReturn(true);
		given(mockPod.getStatus()).willReturn(mockPodStatus);

		watcher.start();
		watcher.eventReceived(Watcher.Action.ADDED, mockPod);

		verify(mockLeadershipController).update();
	}

	@Test
	public void shouldIgnoreEventIfStateDoesNotChange() {
		given(mockPod.getStatus()).willReturn(mockPodStatus);

		watcher.start();
		watcher.eventReceived(Watcher.Action.ADDED, mockPod);

		verify(mockLeadershipController, times(0)).update();
	}

	@Test
	public void shouldHandleClose() {
		watcher.onClose(mockKubernetesClientException);

		verify(mockPodResource).watch(watcher);
	}

	@Test
	public void shouldIgnoreCloseWithoutCause() {
		watcher.onClose(null);

		verify(mockPodResource, times(0)).watch(watcher);
	}

}
