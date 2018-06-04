package org.springframework.cloud.kubernetes.leader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.leader.Candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderContextTest {

	private static final String ROLE = "test-role";

	private static final String ID = "test-id";

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeadershipController mockLeadershipController;

	@Mock
	private Leader mockLeader;

	private LeaderContext leaderContext;

	@Before
	public void before() {
		given(mockCandidate.getRole()).willReturn(ROLE);
		given(mockCandidate.getId()).willReturn(ID);

		leaderContext = new LeaderContext(mockCandidate, mockLeadershipController);
	}

	@Test
	public void testIsLeaderWithoutLeader() {
		boolean result = leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWithAnotherLeader() {
		given(mockLeadershipController.getLeader(ROLE)).willReturn(mockLeader);
		given(mockLeader.getId()).willReturn("another-test-id");

		boolean result = leaderContext.isLeader();

		assertThat(result).isFalse();
	}

	@Test
	public void testIsLeaderWhenLeader() {
		given(mockLeadershipController.getLeader(ROLE)).willReturn(mockLeader);
		given(mockLeader.getId()).willReturn(ID);

		boolean result = leaderContext.isLeader();

		assertThat(result).isTrue();
	}

	@Test
	public void shouldYieldLeadership() {
		leaderContext.yield();

		verify(mockLeadershipController).revoke(mockCandidate);
	}

}
