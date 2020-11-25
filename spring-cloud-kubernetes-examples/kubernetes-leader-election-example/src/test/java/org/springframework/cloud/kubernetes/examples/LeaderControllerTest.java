/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.examples;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LeaderControllerTest {

	@Mock
	private OnGrantedEvent mockOnGrantedEvent;

	@Mock
	private OnRevokedEvent mockOnRevokedEvent;

	@Mock
	private Context mockContext;

	private String host;

	private LeaderController leaderController;

	@BeforeEach
	public void before() throws UnknownHostException {
		this.host = InetAddress.getLocalHost().getHostName();
		this.leaderController = new LeaderController();
	}

	@Test
	public void shouldGetNonLeaderInfo() {
		String message = String.format("I am '%s' but I am not a leader of the 'null'", this.host);
		assertThat(this.leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldHandleGrantedEvent() {
		given(this.mockOnGrantedEvent.getContext()).willReturn(this.mockContext);

		this.leaderController.handleEvent(this.mockOnGrantedEvent);

		String message = String.format("I am '%s' and I am the leader of the 'null'", this.host);
		assertThat(this.leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldHandleRevokedEvent() {
		given(this.mockOnGrantedEvent.getContext()).willReturn(this.mockContext);

		this.leaderController.handleEvent(this.mockOnGrantedEvent);
		this.leaderController.handleEvent(this.mockOnRevokedEvent);

		String message = String.format("I am '%s' but I am not a leader of the 'null'", this.host);
		assertThat(this.leaderController.getInfo()).isEqualTo(message);
	}

	@Test
	public void shouldRevokeLeadership() {
		given(this.mockOnGrantedEvent.getContext()).willReturn(this.mockContext);

		this.leaderController.handleEvent(this.mockOnGrantedEvent);
		ResponseEntity<String> responseEntity = this.leaderController.revokeLeadership();

		String message = String.format("Leadership revoked for '%s'", this.host);
		assertThat(responseEntity.getBody()).isEqualTo(message);
		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(this.mockContext).yield();
	}

	@Test
	public void shouldNotRevokeLeadershipIfNotLeader() {
		ResponseEntity<String> responseEntity = this.leaderController.revokeLeadership();

		String message = String.format("Cannot revoke leadership because '%s' is not a leader", this.host);
		assertThat(responseEntity.getBody()).isEqualTo(message);
		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(this.mockContext, times(0)).yield();
	}

}
