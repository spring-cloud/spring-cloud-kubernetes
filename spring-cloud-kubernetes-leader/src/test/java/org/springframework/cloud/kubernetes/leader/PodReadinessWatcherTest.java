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

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
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

	@BeforeEach
	public void before() {
		this.watcher = new PodReadinessWatcher(POD_NAME, this.mockKubernetesClient,
				this.mockLeadershipController);
	}

	@Test
	public void shouldStartOnce() {
		initStubs();
		this.watcher.start();
		this.watcher.start();

		verify(this.mockPodResource).watch(this.watcher);
	}

	@Test
	public void shouldStopOnce() {
		initStubs();
		this.watcher.start();
		this.watcher.stop();
		this.watcher.stop();

		verify(this.mockWatch).close();
	}

	@Test
	public void shouldHandleEventWithStateChange() {
		initStubs();
		given(this.mockPodResource.isReady()).willReturn(true);
		given(this.mockPod.getStatus()).willReturn(this.mockPodStatus);

		this.watcher.start();
		this.watcher.eventReceived(Watcher.Action.ADDED, this.mockPod);

		verify(this.mockLeadershipController).update();
	}

	@Test
	public void shouldIgnoreEventIfStateDoesNotChange() {
		initStubs();
		given(this.mockPod.getStatus()).willReturn(this.mockPodStatus);

		this.watcher.start();
		this.watcher.eventReceived(Watcher.Action.ADDED, this.mockPod);

		verify(this.mockLeadershipController, times(0)).update();
	}

	@Test
	public void shouldHandleClose() {
		initStubs();
		this.watcher.onClose(this.mockKubernetesClientException);

		verify(this.mockPodResource).watch(this.watcher);
	}

	@Test
	public void shouldIgnoreCloseWithoutCause() {
		this.watcher.onClose(null);

		verify(this.mockPodResource, times(0)).watch(this.watcher);
	}

	private void initStubs() {
		given(this.mockKubernetesClient.pods()).willReturn(this.mockPodsOperation);
		given(this.mockPodsOperation.withName(POD_NAME)).willReturn(this.mockPodResource);
		given(this.mockPodResource.watch(this.watcher)).willReturn(this.mockWatch);
	}

}
