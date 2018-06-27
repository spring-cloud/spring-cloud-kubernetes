package org.springframework.cloud.kubernetes.leader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderTest {

	private static final String ROLE = "test-role";

	private static final String ID = "test-id";

	@Mock
	private LeaderKubernetesHelper mockKubernetesHelper;

	private Leader leader;

	@Before
	public void before() {
		leader = new Leader(ROLE, ID, mockKubernetesHelper);
	}

	@Test
	public void shouldGetRole() {
		assertThat(leader.getRole()).isEqualTo(ROLE);
	}

	@Test
	public void shouldGetId() {
		assertThat(leader.getId()).isEqualTo(ID);
	}

	@Test
	public void shouldCheckValidity() {
		given(mockKubernetesHelper.podExists(ID)).willReturn(true);

		boolean result = leader.isValid();

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).podExists(ID);
	}

}
