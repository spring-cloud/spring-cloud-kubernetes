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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Gytis Trikleris
 */
@ExtendWith(MockitoExtension.class)
public class Fabric8LeaderRecordWatcherTest {

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private Fabric8LeadershipController mockFabric8LeadershipController;

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockConfigMapsOperation;

	@Mock
	private NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mockInNamespaceOperation;

	@Mock
	private Resource<ConfigMap> mockWithNameResource;

	@Mock
	private Watch mockWatch;

	@Mock
	private ConfigMap mockConfigMap;

	@Mock
	private WatcherException mockKubernetesClientException;

	private Fabric8LeaderRecordWatcher watcher;

	@BeforeEach
	public void before() {
		this.watcher = new Fabric8LeaderRecordWatcher(this.mockLeaderProperties, this.mockFabric8LeadershipController,
				this.mockKubernetesClient);
	}

	@Test
	public void shouldStartOnce() {
		initStubs();
		this.watcher.start();
		this.watcher.start();

		verify(this.mockWithNameResource).watch(this.watcher);
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
	public void shouldHandleEvent() {
		this.watcher.eventReceived(Watcher.Action.ADDED, this.mockConfigMap);
		this.watcher.eventReceived(Watcher.Action.DELETED, this.mockConfigMap);
		this.watcher.eventReceived(Watcher.Action.MODIFIED, this.mockConfigMap);

		verify(this.mockFabric8LeadershipController, times(3)).update();
	}

	@Test
	public void shouldIgnoreErrorEvent() {
		this.watcher.eventReceived(Watcher.Action.ERROR, this.mockConfigMap);

		verify(this.mockFabric8LeadershipController, times(0)).update();
	}

	@Test
	public void shouldHandleClose() {
		initStubs();
		this.watcher.onClose(this.mockKubernetesClientException);

		verify(this.mockWithNameResource).watch(this.watcher);
	}

	@Test
	public void shouldIgnoreCloseWithoutCause() {
		this.watcher.onClose(null);

		verify(this.mockWithNameResource, times(0)).watch(this.watcher);
	}

	private void initStubs() {
		given(this.mockKubernetesClient.configMaps()).willReturn(this.mockConfigMapsOperation);
		given(this.mockConfigMapsOperation.inNamespace(null)).willReturn(this.mockInNamespaceOperation);
		given(this.mockInNamespaceOperation.withName(null)).willReturn(this.mockWithNameResource);
		given(this.mockWithNameResource.watch(this.watcher)).willReturn(this.mockWatch);
	}

}
