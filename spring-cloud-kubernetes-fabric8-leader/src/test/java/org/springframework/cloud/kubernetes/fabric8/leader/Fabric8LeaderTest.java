/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.leader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.leader.Leader;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.DefaultCandidate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gytis Trikleris
 */
class Fabric8LeaderTest {

	private static final String ROLE = "test-role";

	private static final String ID = "test-id";

	private Leader leader;

	@BeforeEach
	void before() {
		leader = new Leader(ROLE, ID);
	}

	@Test
	void shouldGetRole() {
		assertThat(leader.getRole()).isEqualTo(ROLE);
	}

	@Test
	void shouldGetId() {
		assertThat(leader.getId()).isEqualTo(ID);
	}

	@Test
	void shouldCheckWithNullCandidate() {
		assertThat(leader.isCandidate(null)).isEqualTo(false);
	}

	@Test
	void shouldCheckCandidate() {
		Candidate candidate = new DefaultCandidate(ID, ROLE);
		assertThat(leader.isCandidate(candidate)).isTrue();
	}

}
