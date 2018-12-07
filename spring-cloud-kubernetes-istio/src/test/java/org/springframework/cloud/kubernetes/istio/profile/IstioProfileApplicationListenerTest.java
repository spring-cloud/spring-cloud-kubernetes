package org.springframework.cloud.kubernetes.istio.profile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.cloud.kubernetes.istio.profile.IstioProfileApplicationListener;
import org.springframework.cloud.kubernetes.istio.utils.MeshUtils;
import org.springframework.cloud.kubernetes.profile.KubernetesProfileApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IstioProfileApplicationListenerTest {

	private static final String[] ACTIVE_PROFILES = new String[0];

	@Mock
	private ConfigurableEnvironment mockEnvironment;

	@Mock
	private MeshUtils mockMeshUtils;

	@Mock
	private ApplicationEnvironmentPreparedEvent mockEvent;

	private IstioProfileApplicationListener listener;

	@Before
	public void before() {
		when(mockEnvironment.getActiveProfiles()).thenReturn(ACTIVE_PROFILES);
		when(mockEvent.getEnvironment()).thenReturn(mockEnvironment);
		listener = new IstioProfileApplicationListener(mockMeshUtils);
	}

	@Test
	public void shouldEnableKubernetesProfile() {
		when(mockMeshUtils.isIstioEnabled()).thenReturn(true);
		listener.onApplicationEvent(mockEvent);
		verify(mockEnvironment).addActiveProfile("istio");
	}

	@Test
	public void shouldNotEnableKubernetesProfile() {
		when(mockMeshUtils.isIstioEnabled()).thenReturn(false);
		listener.onApplicationEvent(mockEvent);
		verify(mockEnvironment, times(0)).addActiveProfile("istio");
	}

}
