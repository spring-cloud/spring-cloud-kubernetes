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
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
class Fabric8LeaderRecordWatcherTest {

	private final LeaderProperties mockLeaderProperties = Mockito.mock(LeaderProperties.class);

	private final Fabric8LeadershipController mockFabric8LeadershipController = Mockito
		.mock(Fabric8LeadershipController.class);

	private final KubernetesClient mockKubernetesClient = Mockito.mock(KubernetesClient.class);

	@SuppressWarnings("unchecked")
	private final MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockConfigMapsOperation = Mockito
		.mock(MixedOperation.class);

	@SuppressWarnings("unchecked")
	private final NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockInNamespaceOperation = Mockito
		.mock(NonNamespaceOperation.class);

	@SuppressWarnings("unchecked")
	private final Resource<ConfigMap> mockWithNameResource = Mockito.mock(Resource.class);

	private final Watch mockWatch = Mockito.mock(Watch.class);

	private final ConfigMap mockConfigMap = Mockito.mock(ConfigMap.class);

	private final WatcherException mockKubernetesClientException = Mockito.mock(WatcherException.class);

	private Fabric8LeaderRecordWatcher watcher;

	@BeforeEach
	void beforeEach() {
		watcher = new Fabric8LeaderRecordWatcher(mockLeaderProperties, mockFabric8LeadershipController,
				mockKubernetesClient);
	}

	@Test
	void shouldStartOnce() {
		initStubs();
		watcher.start();
		watcher.start();
		verify(mockWithNameResource).watch(watcher);
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
	void shouldHandleEvent() {
		watcher.eventReceived(Watcher.Action.ADDED, mockConfigMap);
		watcher.eventReceived(Watcher.Action.DELETED, mockConfigMap);
		watcher.eventReceived(Watcher.Action.MODIFIED, mockConfigMap);
		verify(mockFabric8LeadershipController, times(3)).update();
	}

	@Test
	void shouldIgnoreErrorEvent() {
		watcher.eventReceived(Watcher.Action.ERROR, mockConfigMap);
		verify(mockFabric8LeadershipController, times(0)).update();
	}

	@Test
	void shouldHandleClose() {
		initStubs();
		watcher.onClose(mockKubernetesClientException);
		verify(mockWithNameResource).watch(watcher);
	}

	@Test
	void shouldIgnoreCloseWithoutCause() {
		watcher.onClose(null);
		verify(mockWithNameResource, times(0)).watch(watcher);
	}

	private void initStubs() {
		Mockito.when(mockKubernetesClient.configMaps()).thenReturn(mockConfigMapsOperation);
		Mockito.when(mockConfigMapsOperation.inNamespace(null)).thenReturn(mockInNamespaceOperation);
		Mockito.when(mockInNamespaceOperation.withName(null)).thenReturn(mockWithNameResource);
		Mockito.when(mockWithNameResource.watch(watcher)).thenReturn(mockWatch);
	}

}
