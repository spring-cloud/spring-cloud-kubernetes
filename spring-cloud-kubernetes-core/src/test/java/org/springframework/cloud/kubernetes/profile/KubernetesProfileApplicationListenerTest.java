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
