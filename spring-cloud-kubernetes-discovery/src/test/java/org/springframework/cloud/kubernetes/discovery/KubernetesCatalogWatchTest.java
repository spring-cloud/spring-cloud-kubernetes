package org.springframework.cloud.kubernetes.discovery;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Oleg Vyukov
 */
@RunWith(MockitoJUnitRunner.class)
public class KubernetesCatalogWatchTest {

	@Mock
	private KubernetesDiscoveryClient kubernetesDiscoveryClient;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@InjectMocks
	private KubernetesCatalogWatch underTest;

	@Before
	public void setUp() throws Exception {
		underTest.setApplicationEventPublisher(applicationEventPublisher);
	}

	@Test
	public void testRandomOrder() throws Exception {
		final List<String> services = Arrays.asList("api", "api", "other");
		final List<String> shuffleServices = Arrays.asList("api", "other", "api");
		when(kubernetesDiscoveryClient.getServices()).thenReturn(services);

		underTest.catalogServicesWatch();
		// second execution on shuffleServices
		underTest.catalogServicesWatch();

		verify(applicationEventPublisher).publishEvent(any(HeartbeatEvent.class));
	}
}
