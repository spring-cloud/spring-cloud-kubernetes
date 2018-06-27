/*
 * Copyright (C) 2018 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.leader;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.context.SmartLifecycle;
import org.springframework.integration.leader.Candidate;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeaderInitiator implements SmartLifecycle {

	private final LeaderProperties leaderProperties;

	private final LeadershipController leadershipController;

	private final Candidate candidate;

	private final ScheduledExecutorService scheduledExecutorService;

	private boolean isRunning;

	public LeaderInitiator(LeaderProperties leaderProperties, LeadershipController leadershipController,
		Candidate candidate, ScheduledExecutorService scheduledExecutorService) {
		this.leaderProperties = leaderProperties;
		this.leadershipController = leadershipController;
		this.candidate = candidate;
		this.scheduledExecutorService = scheduledExecutorService;
	}

	@Override
	public boolean isAutoStartup() {
		return leaderProperties.isAutoStartup();
	}

	@Override
	public void start() {
		if (!isRunning()) {
			scheduledExecutorService.execute(this::update);
			isRunning = true;
		}
	}

	@Override
	public void stop() {
		if (isRunning()) {
			scheduledExecutorService.execute(() -> leadershipController.revoke(candidate));
			scheduledExecutorService.shutdown();
			isRunning = false;
		}
	}

	@Override
	public void stop(Runnable runnable) {
		stop();
		runnable.run();
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public int getPhase() {
		return 0; // TODO implement
	}

	private void update() {
		if (leadershipController.acquire(candidate)) {
			// We're a leader, check-in later
			scheduleUpdate(leaderProperties.getLeaseDuration());
		} else {
			// Couldn't become a leader, retry sooner.
			// TODO maybe we should separate error and another leader scenarios?
			scheduleUpdate(leaderProperties.getRetryPeriod());
		}
	}

	private void scheduleUpdate(long waitPeriod) {
		scheduledExecutorService.schedule(this::update, jitter(waitPeriod), TimeUnit.MILLISECONDS);
	}

	private long jitter(long num) {
		return (long) (num * (1 + Math.random() * (leaderProperties.getJitterFactor() - 1)));
	}

}
