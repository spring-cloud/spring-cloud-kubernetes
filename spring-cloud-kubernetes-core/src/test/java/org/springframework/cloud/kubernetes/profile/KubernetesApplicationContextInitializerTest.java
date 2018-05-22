package org.springframework.cloud.kubernetes.profile;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesApplicationContextInitializerTest {

	@Mock
	private Supplier<KubernetesProfileApplicationListener> listenerSupplier;

	@Mock
	private ConfigurableApplicationContext applicationContext;

	@Mock
	private ConfigurableEnvironment environment;

	@Mock
	private KubernetesProfileApplicationListener listener;

	private KubernetesApplicationContextInitializer initializer;

	@Before
	public void setUp() {
		initializer = new KubernetesApplicationContextInitializer(listenerSupplier);
		when(applicationContext.getEnvironment()).thenReturn(environment);
		when(listenerSupplier.get()).thenReturn(listener);
	}

	@Test
	public void kubernetesDisabled() {
		when(environment.getProperty("spring.cloud.kubernetes.enabled", Boolean.class, true))
			.thenReturn(false);

		initializer.initialize(applicationContext);

		verify(listenerSupplier, never()).get();
	}

	@Test
	public void kubernetesEnabled() {
		when(environment.getProperty("spring.cloud.kubernetes.enabled", Boolean.class, true))
			.thenReturn(true);

		initializer.initialize(applicationContext);

		verify(listenerSupplier).get();
		verify(listener).addKubernetesProfile(environment);
	}
}
