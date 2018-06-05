package org.springframework.cloud.kubernetes.leader;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderInitiatorTest {

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private ScheduledExecutorService mockScheduledExecutorService;

	@Mock
	private Runnable mockRunnable;

	private LeaderInitiator leaderInitiator;

	@Before
	public void before() {
		leaderInitiator = new LeaderInitiator(mockLeaderProperties, mockLeadershipController, mockCandidate,
			mockScheduledExecutorService);
	}

	@Test
	public void testIsAutoStartup() {
		assertThat(leaderInitiator.isAutoStartup()).isFalse();
		verify(mockLeaderProperties).isAutoStartup();
	}

	@Test
	public void shouldStartOnlyOnce() {
		leaderInitiator.start();
		leaderInitiator.start();

		assertThat(leaderInitiator.isRunning()).isTrue();
		verify(mockScheduledExecutorService).execute(any());
	}

	@Test
	public void shouldStopOnlyOnce() {
		leaderInitiator.start();
		leaderInitiator.stop();
		leaderInitiator.stop();

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockScheduledExecutorService, times(2)).execute(any());
		verify(mockScheduledExecutorService).shutdown();
	}

	@Test
	public void shouldStopAndExecuteCallback() {
		leaderInitiator.start();
		leaderInitiator.stop(mockRunnable);

		assertThat(leaderInitiator.isRunning()).isFalse();
		verify(mockScheduledExecutorService, times(2)).execute(any());
		verify(mockScheduledExecutorService).shutdown();
		verify(mockRunnable).run();
	}

	@Test
	public void shouldRevokeLeadershipWhenStopping() {
		leaderInitiator.start();
		leaderInitiator.stop();

		ArgumentCaptor<Runnable> revokeRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		verify(mockScheduledExecutorService, times(2)).execute(revokeRunnableCaptor.capture());

		Runnable revokeRunnable = revokeRunnableCaptor.getAllValues().get(1);
		revokeRunnable.run();
		verify(mockLeadershipController).revoke(mockCandidate);
	}

	@Test
	public void shouldScheduleUpdateIfLeadershipIsAcquired() {
		given(mockLeadershipController.acquire(mockCandidate)).willReturn(true);
		given(mockLeaderProperties.getLeaseDuration()).willReturn(1000L);
		given(mockLeaderProperties.getJitterFactor()).willReturn(1.0);
		leaderInitiator.start();

		ArgumentCaptor<Runnable> updateRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		verify(mockScheduledExecutorService).execute(updateRunnableCaptor.capture());

		Runnable updateRunnable = updateRunnableCaptor.getValue();
		updateRunnable.run();
		verify(mockLeadershipController).acquire(mockCandidate);
		verify(mockScheduledExecutorService).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void shouldScheduleUpdateIfLeadershipIsNotAcquired() {
		given(mockLeaderProperties.getRetryPeriod()).willReturn(1000L);
		given(mockLeaderProperties.getJitterFactor()).willReturn(1.0);
		leaderInitiator.start();

		ArgumentCaptor<Runnable> updateRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		verify(mockScheduledExecutorService).execute(updateRunnableCaptor.capture());

		Runnable updateRunnable = updateRunnableCaptor.getValue();
		updateRunnable.run();
		verify(mockLeadershipController).acquire(mockCandidate);
		verify(mockScheduledExecutorService).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
	}

}
