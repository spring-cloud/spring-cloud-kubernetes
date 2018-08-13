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

package org.springframework.cloud.kubernetes.profile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesProfileApplicationListenerTest {

	private static final String[] ACTIVE_PROFILES = new String[0];

	@Mock
	private ConfigurableEnvironment mockEnvironment;

	@Mock
	private PodUtils mockPodUtils;

	@Mock
	private ApplicationEnvironmentPreparedEvent mockEvent;

	private KubernetesProfileApplicationListener listener;

	@Before
	public void before() {
		when(mockEnvironment.getActiveProfiles()).thenReturn(ACTIVE_PROFILES);
		when(mockEvent.getEnvironment()).thenReturn(mockEnvironment);
		listener = new KubernetesProfileApplicationListener(mockPodUtils);
	}

	@Test
	public void shouldEnableKubernetesProfile() {
		when(mockPodUtils.isInsideKubernetes()).thenReturn(true);
		listener.onApplicationEvent(mockEvent);
		verify(mockEnvironment).addActiveProfile("kubernetes");
	}

	@Test
	public void shouldNotEnableKubernetesProfile() {
		when(mockPodUtils.isInsideKubernetes()).thenReturn(false);
		listener.onApplicationEvent(mockEvent);
		verify(mockEnvironment, times(0)).addActiveProfile("kubernetes");
	}

}
