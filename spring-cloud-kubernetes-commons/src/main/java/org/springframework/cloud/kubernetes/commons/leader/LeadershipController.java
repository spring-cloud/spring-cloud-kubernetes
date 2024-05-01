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

import org.springframework.core.log.LogAccessor;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public abstract class LeadershipController {

	private static final LogAccessor LOGGER = new LogAccessor(LeadershipController.class);

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
		return Optional.ofNullable(localLeader);
	}

	public abstract void update();

	public abstract void revoke();

	protected String getLeaderKey() {
		return leaderProperties.getLeaderIdPrefix() + candidate.getRole();
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
		LOGGER.debug(() -> "retrieved leaderId: " + leaderId + " from leaderKey : " + leaderId);
		if (!StringUtils.hasText(leaderId)) {
			return null;
		}

		return new Leader(candidate.getRole(), leaderId);
	}

	protected void handleLeaderChange(Leader newLeader) {
		if (Objects.equals(localLeader, newLeader)) {
			LOGGER.debug(() -> "Leader is still : " + localLeader);
			return;
		}

		Leader oldLeader = localLeader;
		localLeader = newLeader;

		if (oldLeader != null && oldLeader.isCandidate(candidate)) {
			notifyOnRevoked();
		}
		else if (newLeader != null && newLeader.isCandidate(candidate)) {
			notifyOnGranted();
		}

		restartLeaderReadinessWatcher();

		LOGGER.debug(() -> "New leader is " + localLeader);
	}

	protected void notifyOnGranted() {
		LOGGER.debug(() -> "Leadership has been granted to : " + candidate);

		Context context = new LeaderContext(candidate, this);
		leaderEventPublisher.publishOnGranted(this, context, candidate.getRole());
		try {
			candidate.onGranted(context);
		}
		catch (InterruptedException e) {
			LOGGER.warn(e::getMessage);
			Thread.currentThread().interrupt();
		}
	}

	protected void notifyOnRevoked() {
		LOGGER.debug(() -> "Leadership has been revoked from :" + candidate);

		Context context = new LeaderContext(candidate, this);
		leaderEventPublisher.publishOnRevoked(this, context, candidate.getRole());
		candidate.onRevoked(context);
	}

	protected void notifyOnFailedToAcquire() {
		if (leaderProperties.isPublishFailedEvents()) {
			Context context = new LeaderContext(candidate, this);
			leaderEventPublisher.publishOnFailedToAcquire(this, context, candidate.getRole());
		}
	}

	protected void restartLeaderReadinessWatcher() {
		if (leaderReadinessWatcher != null) {
			leaderReadinessWatcher.stop();
			leaderReadinessWatcher = null;
		}

		if (localLeader != null && !localLeader.isCandidate(candidate)) {
			leaderReadinessWatcher = createPodReadinessWatcher(localLeader.getId());
			leaderReadinessWatcher.start();
		}
	}

	protected abstract PodReadinessWatcher createPodReadinessWatcher(String localLeaderId);

}
