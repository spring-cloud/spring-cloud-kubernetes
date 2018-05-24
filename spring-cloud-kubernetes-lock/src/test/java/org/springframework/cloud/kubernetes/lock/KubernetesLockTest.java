package org.springframework.cloud.kubernetes.lock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesLockTest {

	private static final String NAME = "test-name";

	private static final String HOLDER = "test-holder";

	@Mock
	private ConfigMapLockRepository repository;

	@Test
	public void shouldLock() {
		given(repository.create(NAME, HOLDER, 0)).willReturn(true);

		KubernetesLock lock = new KubernetesLock(repository, NAME, HOLDER, 0);
		lock.lock();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void shouldWaitUntilLockCanBeAcquired() {
		given(repository.create(NAME, HOLDER, 0)).will(new Answer<Boolean>() {
			private int callsCounter = 0;

			@Override
			public Boolean answer(InvocationOnMock invocationOnMock) {
				return callsCounter++ > 0;
			}
		});

		KubernetesLock lock = new KubernetesLock(repository, NAME, HOLDER, 0);
		lock.lock();

		verify(repository).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test
	public void lockShouldNotBeInterrupted() {
		given(repository.create(NAME, HOLDER, 0)).will(new Answer<Boolean>() {
			private int callsCounter = 0;

			@Override
			public Boolean answer(InvocationOnMock invocationOnMock) throws InterruptedException {
				if (callsCounter++ > 0) {
					return true;
				}
				throw new InterruptedException("test exception");
			}
		});

		KubernetesLock lock = new KubernetesLock(repository, NAME, HOLDER, 0);
		lock.lock();

		verify(repository).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test
	public void shouldLockWithLockInterruptibly() {

	}

	@Test
	public void lockInterruptiblyShouldBeInterrupted() {

	}

	@Test
	public void shouldLockWithTryLock() {

	}

	@Test
	public void shouldFailToLockWithTryLock() {

	}

	@Test
	public void shouldFailWithTryLockTimeout() {

	}

	@Test
	public void shouldUnlock() {

	}

	@Test(expected = UnsupportedOperationException.class)
	public void newConditionShouldFail() {
		KubernetesLock lock = new KubernetesLock(repository, NAME, HOLDER, 0);
		lock.newCondition();
	}

}
