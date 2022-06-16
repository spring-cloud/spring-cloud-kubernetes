/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public abstract class LeadershipController {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeadershipController.class);

	protected static final String PROVIDER_KEY = "provider";

	protected static final String PROVIDER = "spring-cloud-kubernetes";

	protected static final String KIND_KEY = "kind";

	protected static final String KIND = "leaders";

	protected Candidate candidate;

	protected Leader localLeader;

	protected LeaderProperties leaderProperties;

	protected LeaderEventPublisher leaderEventPublisher;

	protected PodReadinessWatcher leaderReadinessWatcher;

	public LeadershipController(Candidate candidate, LeaderProperties leaderProperties,
			LeaderEventPublisher leaderEventPublisher) {
		this.candidate = candidate;
		this.leaderProperties = leaderProperties;
		this.leaderEventPublisher = leaderEventPublisher;
	}

	public Optional<Leader> getLocalLeader() {
		return Optional.ofNullable(this.localLeader);
	}

	public abstract void update();

	public abstract void revoke();

	protected String getLeaderKey() {
		return this.leaderProperties.getLeaderIdPrefix() + this.candidate.getRole();
	}

	protected Map<String, String> getLeaderData(Candidate candidate) {
		String leaderKey = getLeaderKey();
		return Collections.singletonMap(leaderKey, candidate.getId());
	}

	protected Leader extractLeader(Map<String, String> data) {
		if (data == null) {
			return null;
		}

		String leaderKey = getLeaderKey();
		String leaderId = data.get(leaderKey);
		if (!StringUtils.hasText(leaderId)) {
			return null;
		}

		return new Leader(this.candidate.getRole(), leaderId);
	}

	protected void handleLeaderChange(Leader newLeader) {
		if (Objects.equals(this.localLeader, newLeader)) {
			LOGGER.debug("Leader is still '{}'", this.localLeader);
			return;
		}

		Leader oldLeader = this.localLeader;
		this.localLeader = newLeader;

		if (oldLeader != null && oldLeader.isCandidate(this.candidate)) {
			notifyOnRevoked();
		}
		else if (newLeader != null && newLeader.isCandidate(this.candidate)) {
			notifyOnGranted();
		}

		restartLeaderReadinessWatcher();

		LOGGER.debug("New leader is '{}'", this.localLeader);
	}

	protected void notifyOnGranted() {
		LOGGER.debug("Leadership has been granted for '{}'", this.candidate);

		Context context = new LeaderContext(this.candidate, this);
		this.leaderEventPublisher.publishOnGranted(this, context, this.candidate.getRole());
		try {
			this.candidate.onGranted(context);
		}
		catch (InterruptedException e) {
			LOGGER.warn(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	protected void notifyOnRevoked() {
		LOGGER.debug("Leadership has been revoked for '{}'", this.candidate);

		Context context = new LeaderContext(this.candidate, this);
		this.leaderEventPublisher.publishOnRevoked(this, context, this.candidate.getRole());
		this.candidate.onRevoked(context);
	}

	protected void notifyOnFailedToAcquire() {
		if (this.leaderProperties.isPublishFailedEvents()) {
			Context context = new LeaderContext(this.candidate, this);
			this.leaderEventPublisher.publishOnFailedToAcquire(this, context, this.candidate.getRole());
		}
	}

	protected void restartLeaderReadinessWatcher() {
		if (this.leaderReadinessWatcher != null) {
			this.leaderReadinessWatcher.stop();
			this.leaderReadinessWatcher = null;
		}

		if (this.localLeader != null && !this.localLeader.isCandidate(this.candidate)) {
			this.leaderReadinessWatcher = createPodReadinessWatcher(this.localLeader.getId());
			this.leaderReadinessWatcher.start();
		}
	}

	protected abstract PodReadinessWatcher createPodReadinessWatcher(String localLeaderId);

}
