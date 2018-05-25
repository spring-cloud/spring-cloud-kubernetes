package org.springframework.cloud.kubernetes.lock;

import java.util.concurrent.locks.Lock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesLockRegistryTest {

	@Mock
	private ConfigMapLockRepository mockRepository;

	private KubernetesLockRegistry registry;

	@Before
	public void before() {
		registry = new KubernetesLockRegistry(mockRepository, "test-id");
	}

	@Test
	public void shouldObtain() {
		Lock actualLock = registry.obtain("test-name");
		KubernetesLock expectedLock = new KubernetesLock(mockRepository, "test-name", "test-id", System.currentTimeMillis());

		assertThat(actualLock).isEqualToComparingOnlyGivenFields(expectedLock, "name", "holder");
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailToObtainWithNonStringKey() {
		registry.obtain(new Object());
	}

	@Test
	public void expireUnusedOlderThanShouldDelegate() {
		registry.obtain("test-name-1");
		registry.obtain("test-name-2");
		registry.expireUnusedOlderThan(100);

		verify(mockRepository).deleteIfOlderThan("test-name-1", 100);
		verify(mockRepository).deleteIfOlderThan("test-name-2", 100);
	}

}
