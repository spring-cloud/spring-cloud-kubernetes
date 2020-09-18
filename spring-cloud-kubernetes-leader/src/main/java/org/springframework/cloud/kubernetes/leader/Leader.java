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

package org.springframework.cloud.kubernetes.leader;

import java.util.Objects;

import org.springframework.integration.leader.Candidate;

/**
 * @author Gytis Trikleris
 */
public class Leader {

	private final String role;

	private final String id;

	public Leader(String role, String id) {
		this.role = role;
		this.id = id;
	}

	public String getRole() {
		return this.role;
	}

	public String getId() {
		return this.id;
	}

	public boolean isCandidate(Candidate candidate) {
		if (candidate == null) {
			return false;
		}

		return Objects.equals(this.role, candidate.getRole()) && Objects.equals(this.id, candidate.getId());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Leader leader = (Leader) o;

		return Objects.equals(this.role, leader.role) && Objects.equals(this.id, leader.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.role, this.id);
	}

	@Override
	public String toString() {
		return String.format("Leader{role='%s', id='%s'}", this.role, this.id);
	}

}
