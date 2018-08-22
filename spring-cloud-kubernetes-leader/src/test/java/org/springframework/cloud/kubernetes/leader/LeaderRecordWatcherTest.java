/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.leader;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
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
public class LeaderRecordWatcherTest {

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		mockConfigMapsOperation;

	@Mock
	private NonNamespaceOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		mockInNamespaceOperation;

	@Mock
	private Resource<ConfigMap, DoneableConfigMap> mockWithNameResource;

	@Mock
	private Watch mockWatch;

	@Mock
	private ConfigMap mockConfigMap;

	@Mock
	private KubernetesClientException mockKubernetesClientException;

	private LeaderRecordWatcher watcher;

	@Before
	public void before() {
		watcher = new LeaderRecordWatcher(mockLeaderProperties, mockLeadershipController, mockKubernetesClient);

		given(mockKubernetesClient.configMaps()).willReturn(mockConfigMapsOperation);
		given(mockConfigMapsOperation.inNamespace(null)).willReturn(mockInNamespaceOperation);
		given(mockInNamespaceOperation.withName(null)).willReturn(mockWithNameResource);
		given(mockWithNameResource.watch(watcher)).willReturn(mockWatch);
	}

	@Test
	public void shouldStartOnce() {
		watcher.start();
		watcher.start();

		verify(mockWithNameResource).watch(watcher);
	}

	@Test
	public void shouldStopOnce() {
		watcher.start();
		watcher.stop();
		watcher.stop();

		verify(mockWatch).close();
	}

	@Test
	public void shouldHandleEvent() {
		watcher.eventReceived(Watcher.Action.ADDED, mockConfigMap);
		watcher.eventReceived(Watcher.Action.DELETED, mockConfigMap);
		watcher.eventReceived(Watcher.Action.MODIFIED, mockConfigMap);

		verify(mockLeadershipController, times(3)).update();
	}

	@Test
	public void shouldIgnoreErrorEvent() {
		watcher.eventReceived(Watcher.Action.ERROR, mockConfigMap);

		verify(mockLeadershipController, times(0)).update();
	}

	@Test
	public void shouldHandleClose() {
		watcher.onClose(mockKubernetesClientException);

		verify(mockWithNameResource).watch(watcher);
	}

	@Test
	public void shouldIgnoreCloseWithoutCause() {
		watcher.onClose(null);

		verify(mockWithNameResource, times(0)).watch(watcher);
	}

}
