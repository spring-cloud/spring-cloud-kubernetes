package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
	private LeaderKubernetesHelper mockKubernetesHelper;

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
	public void shouldAcquireWithoutExistingConfigMap() throws InterruptedException {
		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).createConfigMap(Collections.singletonMap(PREFIX + ROLE, ID));
		verifyPublishOnGranted();
	}

	@Test
	public void shouldAcquireWithExistingConfigMap() throws InterruptedException {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).updateConfigMapEntry(mockConfigMap, leaderData);
		verifyPublishOnGranted();
	}

	@Test
	public void shouldAcquireWithoutEventsIfAlreadyLeader() throws InterruptedException {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.podExists(ID)).willReturn(true);
		given(mockConfigMap.getData()).willReturn(leaderData);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		verify(mockCandidate, times(0)).onGranted(any());
		verify(mockKubernetesHelper, times(0)).createConfigMap(any());
		verify(mockKubernetesHelper, times(0)).updateConfigMapEntry(any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnGranted(any(), any(), any());
	}

	@Test
	public void shouldTakeOverLeadershipFromInvalidLeader() throws InterruptedException {
		String anotherId = "another-test-id";
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.podExists(ID)).willReturn(false);
		given(mockConfigMap.getData()).willReturn(leaderData);
		given(mockCandidate.getId()).willReturn(anotherId);

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isTrue();
		Map<String, String> anotherLeaderData = Collections.singletonMap(PREFIX + ROLE, anotherId);
		verify(mockKubernetesHelper).updateConfigMapEntry(mockConfigMap, anotherLeaderData);
		verifyPublishOnGranted();
	}

	@Test
	public void shouldFailToAcquireIfThereIsAnotherLeader() {
		given(mockLeaderProperties.isPublishFailedEvents()).willReturn(true);
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockKubernetesHelper.podExists(ID)).willReturn(true);
		given(mockConfigMap.getData()).willReturn(leaderData);
		given(mockCandidate.getId()).willReturn("another-test-id");

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isFalse();
		verify(mockKubernetesHelper, times(0)).createConfigMap(any());
		verify(mockKubernetesHelper, times(0)).updateConfigMapEntry(any(), any());
		verifyPublishOnFailedToAcquire();
	}

	@Test
	public void shouldFailToAcquireBecauseOfException() {
		given(mockLeaderProperties.isPublishFailedEvents()).willReturn(true);
		doThrow(new KubernetesClientException("Test exception")).when(mockKubernetesHelper).createConfigMap(any());

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isFalse();
		verifyPublishOnFailedToAcquire();
	}

	@Test
	public void shouldFailToAcquireAndSuspendEvents() {
		doThrow(new KubernetesClientException("Test exception")).when(mockKubernetesHelper).createConfigMap(any());

		boolean result = leadershipController.acquire(mockCandidate);

		assertThat(result).isFalse();
		verify(mockLeaderEventPublisher, times(0)).publishOnFailedToAcquire(any(), any(), any());
	}

	@Test
	public void shouldRevokeLeadership() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(leaderData);

		boolean result = leadershipController.revoke(mockCandidate);

		assertThat(result).isTrue();
		verify(mockKubernetesHelper).removeConfigMapEntry(mockConfigMap, PREFIX + ROLE);
		verifyPublishOnRevoked();
	}

	@Test
	public void shouldRevokeLeadershipWithoutEventsIfThereIsNoConfigMap() {
		boolean result = leadershipController.revoke(mockCandidate);

		assertThat(result).isTrue();
		verify(mockCandidate, times(0)).onRevoked(any());
		verify(mockKubernetesHelper, times(0)).removeConfigMapEntry(any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnRevoked(any(), any(), any());
	}

	@Test
	public void shouldRevokeLeadershipWithoutEventsIfThereIsNoLeader() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);

		boolean result = leadershipController.revoke(mockCandidate);

		assertThat(result).isTrue();
		verify(mockCandidate, times(0)).onRevoked(any());
		verify(mockKubernetesHelper, times(0)).removeConfigMapEntry(any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnRevoked(any(), any(), any());
	}

	@Test
	public void shouldRevokeLeadershipWithoutEventsIfThereIsAnotherLeader() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(leaderData);
		given(mockCandidate.getId()).willReturn("another-test-id");

		boolean result = leadershipController.revoke(mockCandidate);

		assertThat(result).isTrue();
		verify(mockCandidate, times(0)).onRevoked(any());
		verify(mockKubernetesHelper, times(0)).removeConfigMapEntry(any(), any());
		verify(mockLeaderEventPublisher, times(0)).publishOnRevoked(any(), any(), any());
	}

	@Test
	public void shouldFailToRevokeLeadership() {
		given(mockKubernetesHelper.getConfigMap()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(leaderData);
		doThrow(new KubernetesClientException("Test exception")).when(mockKubernetesHelper)
			.removeConfigMapEntry(any(), any());

		boolean result = leadershipController.revoke(mockCandidate);

		assertThat(result).isFalse();
		verify(mockCandidate, times(0)).onRevoked(any());
		verify(mockLeaderEventPublisher, times(0)).publishOnRevoked(any(), any(), any());
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

	private void verifyPublishOnGranted() throws InterruptedException {
		ArgumentCaptor<LeaderContext> leaderContextCaptor = ArgumentCaptor.forClass(LeaderContext.class);
		verify(mockLeaderEventPublisher).publishOnGranted(eq(leadershipController),
			leaderContextCaptor.capture(), eq(ROLE));
		LeaderContext expectedLeaderContext = new LeaderContext(mockCandidate, leadershipController);
		assertThat(leaderContextCaptor.getValue()).isEqualToComparingFieldByField(expectedLeaderContext);

		verify(mockCandidate).onGranted(leaderContextCaptor.getValue());
	}

	private void verifyPublishOnRevoked() {
		ArgumentCaptor<LeaderContext> leaderContextCaptor = ArgumentCaptor.forClass(LeaderContext.class);
		verify(mockLeaderEventPublisher).publishOnRevoked(eq(leadershipController),
			leaderContextCaptor.capture(), eq(ROLE));
		LeaderContext expectedLeaderContext = new LeaderContext(mockCandidate, leadershipController);
		assertThat(leaderContextCaptor.getValue()).isEqualToComparingFieldByField(expectedLeaderContext);

		verify(mockCandidate).onRevoked(leaderContextCaptor.getValue());
	}

	public void verifyPublishOnFailedToAcquire() {
		ArgumentCaptor<LeaderContext> leaderContextCaptor = ArgumentCaptor.forClass(LeaderContext.class);
		verify(mockLeaderEventPublisher).publishOnFailedToAcquire(eq(leadershipController),
			leaderContextCaptor.capture(), eq(ROLE));
		LeaderContext expectedLeaderContext = new LeaderContext(mockCandidate, leadershipController);
		assertThat(leaderContextCaptor.getValue()).isEqualToComparingFieldByField(expectedLeaderContext);
	}

}
