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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
public class Fabric8PodReadinessWatcherTest {

	private static final String POD_NAME = "test-pod";

	private final Fabric8LeadershipController mockFabric8LeadershipController = Mockito
		.mock(Fabric8LeadershipController.class);

	private final KubernetesClient mockKubernetesClient = Mockito.mock(KubernetesClient.class);

	@SuppressWarnings("unchecked")
	private final MixedOperation<Pod, PodList, PodResource> mockPodsOperation = Mockito.mock(MixedOperation.class);

	private final PodResource mockPodResource = Mockito.mock(PodResource.class);

	private final Pod mockPod = Mockito.mock(Pod.class);

	private final PodStatus mockPodStatus = Mockito.mock(PodStatus.class);

	private final Watch mockWatch = Mockito.mock(Watch.class);

	private final WatcherException mockKubernetesClientException = Mockito.mock(WatcherException.class);

	private Fabric8PodReadinessWatcher watcher;

	@BeforeEach
	void beforeEach() {
		watcher = new Fabric8PodReadinessWatcher(POD_NAME, mockKubernetesClient, mockFabric8LeadershipController);
	}

	@Test
	void shouldStartOnce() {
		initStubs();
		watcher.start();
		watcher.start();
		verify(mockPodResource).watch(watcher);
	}

	@Test
	void shouldStopOnce() {
		initStubs();
		watcher.start();
		watcher.stop();
		watcher.stop();
		verify(mockWatch).close();
	}

	@Test
	void shouldHandleEventWithStateChange() {
		initStubs();
		Mockito.when(mockPodResource.isReady()).thenReturn(true);
		Mockito.when(mockPod.getStatus()).thenReturn(mockPodStatus);

		watcher.start();
		watcher.eventReceived(Watcher.Action.ADDED, mockPod);
		verify(mockFabric8LeadershipController).update();
	}

	@Test
	void shouldIgnoreEventIfStateDoesNotChange() {
		initStubs();
		Mockito.when(mockPod.getStatus()).thenReturn(mockPodStatus);

		watcher.start();
		watcher.eventReceived(Watcher.Action.ADDED, mockPod);
		verify(mockFabric8LeadershipController, times(0)).update();
	}

	@Test
	void shouldHandleClose() {
		initStubs();
		watcher.onClose(mockKubernetesClientException);
		verify(mockPodResource).watch(watcher);
	}

	@Test
	void shouldIgnoreCloseWithoutCause() {
		watcher.onClose(null);
		verify(mockPodResource, times(0)).watch(watcher);
	}

	private void initStubs() {
		Mockito.when(mockKubernetesClient.pods()).thenReturn(mockPodsOperation);
		Mockito.when(mockPodsOperation.withName(POD_NAME)).thenReturn(mockPodResource);
		Mockito.when(mockPodResource.watch(watcher)).thenReturn(mockWatch);
	}

}
