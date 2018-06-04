package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeadershipControllerTest {

	private final String PREFIX = "leader.";

	private final String ROLE = "test-role";

	private final String ID = "test-id";

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private KubernetesHelper mockKubernetesHelper;

	@Mock
	private ConfigMap mockConfigMap;

	@Mock
	private Candidate mockCandidate;

	@Mock
	private LeaderEventPublisher mockLeaderEventPublisher;

	private Map<String, String> leaderData = Collections.singletonMap(PREFIX + ROLE, ID);

	private LeadershipController leadershipController;

	@Before
	public void before() {
		given(mockLeaderProperties.getLeaderIdPrefix()).willReturn(PREFIX);
		given(mockCandidate.getId()).willReturn(ID);
		given(mockCandidate.getRole()).willReturn(ROLE);

		leadershipController =
			new LeadershipController(mockLeaderProperties, mockKubernetesHelper, mockLeaderEventPublisher);
	}

	@Test
	public void shouldAcquireWithoutExistingConfigMap() {
		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).createConfigMap(Collections.singletonMap(PREFIX + ROLE, ID));
		verify(mockLeaderEventPublisher).publishOnGranted(leadershipController, null, ROLE);
	}

	@Test
	public void shouldAcquireWithExistingConfigMap() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).updateConfigMap(mockConfigMap, leaderData);
		verify(mockLeaderEventPublisher).publishOnGranted(leadershipController, null, ROLE);
	}

	@Test
	public void shouldNotAcquireIfAlreadyLeader() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.isPodAlive(ID)).willReturn(true);
		given(mockConfigMap.getData()).willReturn(leaderData);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper, times(0)).createConfigMap(any());
		verify(mockKubernetesHelper, times(0)).updateConfigMap(any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnGranted(any(), any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnRevoked(any(), any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnFailedToAcquire(any(), any(), any());
	}

	@Test
	public void shouldTakeOverLeadershipFromInvalidLeader() {
		String anotherId = "another-test-id";
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.isPodAlive(ID)).willReturn(false);
		given(mockConfigMap.getData()).willReturn(leaderData);
		given(mockCandidate.getId()).willReturn(anotherId);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).updateConfigMap(mockConfigMap, Collections.singletonMap(PREFIX + ROLE, anotherId));
		verify(mockLeaderEventPublisher).publishOnGranted(leadershipController, null, ROLE);
	}

	@Test
	public void shouldFailToAcquireBecauseOfExistingLeader() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.isPodAlive(ID)).willReturn(true);
		given(mockConfigMap.getData()).willReturn(leaderData);
		given(mockCandidate.getId()).willReturn("another-test-id");

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isFalse();
		verify(mockKubernetesHelper, times(0)).createConfigMap(any());
		verify(mockKubernetesHelper, times(0)).updateConfigMap(any(), any());
		verify(mockLeaderEventPublisher).publishOnFailedToAcquire(leadershipController, null, ROLE);
	}

	@Test
	public void shouldFailToAcquireBecauseOfException() {
		doThrow(new KubernetesClientException("Test exception")).when(mockKubernetesHelper).createConfigMap(any());

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isFalse();
		verify(mockLeaderEventPublisher).publishOnFailedToAcquire(leadershipController, null, ROLE);
	}

	@Test
	public void shouldGetLeader() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(leaderData);

		Leader leader = leadershipController.getLeader(ROLE);

		assertThat(leader.getRole()).isEqualTo(ROLE);
		assertThat(leader.getId()).isEqualTo(ID);
	}

	@Test
	public void shouldNotGetLeaderFromNonExistingConfigMap() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(null);

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldNotGetLeaderFromEmptyConfigMap() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(null);

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldNotGetLeaderFromInvalidConfigMap() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(Collections.emptyMap());

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldHandleFailureWhenGettingLeader() {
		given(mockKubernetesHelper.getConfigMap()).willThrow(new KubernetesClientException("Test exception"));

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

}
