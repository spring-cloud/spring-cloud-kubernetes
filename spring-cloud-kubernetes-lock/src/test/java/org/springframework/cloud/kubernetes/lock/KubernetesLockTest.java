package org.springframework.cloud.kubernetes.lock;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesLockTest {

	private static final String NAME = "test-name";

	private static final String HOLDER = "test-holder";

	@Mock
	private ConfigMapLockRepository repository;

	private KubernetesLock lock;

	@Before
	public void before() {
		lock = new KubernetesLock(repository, NAME, HOLDER, 0);
	}

	@Test
	public void shouldLock() {
		given(repository.create(NAME, HOLDER, 0)).willReturn(true);

		lock.lock();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void lockShouldWaitUntilLockCanBeAcquired() {
		given(repository.create(NAME, HOLDER, 0)).will(new LockInSecondCall());

		lock.lock();

		verify(repository, times(2)).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test
	public void lockShouldNotBeInterrupted() {
		given(repository.create(NAME, HOLDER, 0)).will(new InterrupFirstCall());

		lock.lock();

		verify(repository, times(2)).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test
	public void shouldLockWithLockInterruptibly() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).willReturn(true);

		lock.lockInterruptibly();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void lockInterruptiblyShouldWaitUntilLockCanBeAcquired() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).will(new LockInSecondCall());

		lock.lockInterruptibly();

		verify(repository, times(2)).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test(expected = InterruptedException.class)
	public void lockInterruptiblyShouldBeInterrupted() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).will(new InterrupFirstCall());

		lock.lockInterruptibly();
	}

	@Test
	public void shouldLockWithTryLock() {
		given(repository.create(NAME, HOLDER, 0)).willReturn(true);

		assertThat(lock.tryLock()).isTrue();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void shouldFailToLockWithTryLock() {
		given(repository.create(NAME, HOLDER, 0)).willReturn(false);

		assertThat(lock.tryLock()).isFalse();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void shouldLockWithTryLockTimeout() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).willReturn(true);

		assertThat(lock.tryLock(2, TimeUnit.SECONDS)).isTrue();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test
	public void tryLockShouldWaitUntilLockIsAvailable() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).will(new LockInSecondCall());

		assertThat(lock.tryLock(2, TimeUnit.SECONDS)).isTrue();

		verify(repository, times(2)).deleteIfExpired(NAME);
		verify(repository, times(2)).create(NAME, HOLDER, 0);
	}

	@Test
	public void tryLockShouldTimeout() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).will(new LockInSecondCall());

		assertThat(lock.tryLock(90, TimeUnit.MILLISECONDS)).isFalse();

		verify(repository).deleteIfExpired(NAME);
		verify(repository).create(NAME, HOLDER, 0);
	}

	@Test(expected = InterruptedException.class)
	public void tryLockShouldBeInterrupted() throws InterruptedException {
		given(repository.create(NAME, HOLDER, 0)).will(new InterrupFirstCall());

		lock.tryLock(90, TimeUnit.MILLISECONDS);
	}

	@Test
	public void shouldUnlock() {

	}

	@Test(expected = UnsupportedOperationException.class)
	public void newConditionShouldFail() {
		lock.newCondition();
	}

	private static class LockInSecondCall implements Answer<Boolean> {
		private int callsCounter = 0;

		@Override
		public Boolean answer(InvocationOnMock invocationOnMock) {
			return callsCounter++ > 0;
		}
	}

	private static class InterrupFirstCall implements Answer<Boolean> {
		private int callsCounter = 0;

		@Override
		public Boolean answer(InvocationOnMock invocationOnMock) throws InterruptedException {
			if (callsCounter++ > 0) {
				return true;
			}
			throw new InterruptedException("test exception");
		}
	}

}
