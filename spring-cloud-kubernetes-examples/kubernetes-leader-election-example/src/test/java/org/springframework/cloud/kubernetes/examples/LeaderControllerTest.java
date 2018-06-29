package org.springframework.cloud.kubernetes.examples;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class LeaderControllerTest {

	@Mock
	private OnGrantedEvent mockOnGrantedEvent;

	@Mock
	private OnRevokedEvent mockOnRevokedEvent;

	@Mock
	private Context mockContext;

	private String host;

	private LeaderController leaderController;

	@Before
	public void before() throws UnknownHostException {
		host = InetAddress.getLocalHost().getHostName();
		leaderController = new LeaderController();
	}

	@Test
	public void shouldGetNonLeaderInfo() {
		String message = String.format("I am '%s' but I am not a leader of the 'null'", host);
		assertThat(leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldHandleGrantedEvent() {
		given(mockOnGrantedEvent.getContext()).willReturn(mockContext);

		leaderController.handleEvent(mockOnGrantedEvent);

		String message = String.format("I am '%s' and I am the leader of the 'null'", host);
		assertThat(leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldHandleRevokedEvent() {
		given(mockOnGrantedEvent.getContext()).willReturn(mockContext);

		leaderController.handleEvent(mockOnGrantedEvent);
		leaderController.handleEvent(mockOnRevokedEvent);

		String message = String.format("I am '%s' but I am not a leader of the 'null'", host);
		assertThat(leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldRevokeLeadership() {
		given(mockOnGrantedEvent.getContext()).willReturn(mockContext);

		leaderController.handleEvent(mockOnGrantedEvent);
		ResponseEntity<String> responseEntity = leaderController.revokeLeadership();

		String message = String.format("Leadership revoked for '%s'", host);
		assertThat(responseEntity.getBody()).isEqualTo(message);
		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(mockContext).yield();
	}

	@Test
	public void shouldNotRevokeLeadershipIfNotLeader() {
		ResponseEntity<String> responseEntity = leaderController.revokeLeadership();

		String message = String.format("Cannot revoke leadership because '%s' is not a leader", host);
		assertThat(responseEntity.getBody()).isEqualTo(message);
		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(mockContext, times(0)).yield();
	}

}
