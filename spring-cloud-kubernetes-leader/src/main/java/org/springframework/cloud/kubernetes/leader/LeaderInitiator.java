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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.SmartLifecycle;

/**
 * @author Gytis Trikleris
 */
public class LeaderInitiator implements SmartLifecycle {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeaderInitiator.class);

	private final LeaderProperties leaderProperties;

	private final LeadershipController leadershipController;

	private final LeaderRecordWatcher leaderRecordWatcher;

	private final PodReadinessWatcher hostPodWatcher;

	private ScheduledExecutorService scheduledExecutorService;

	private boolean isRunning;

	public LeaderInitiator(LeaderProperties leaderProperties, LeadershipController leadershipController,
			LeaderRecordWatcher leaderRecordWatcher, PodReadinessWatcher hostPodWatcher) {
		this.leaderProperties = leaderProperties;
		this.leadershipController = leadershipController;
		this.leaderRecordWatcher = leaderRecordWatcher;
		this.hostPodWatcher = hostPodWatcher;
	}

	@Override
	public boolean isAutoStartup() {
		return this.leaderProperties.isAutoStartup();
	}

	@Override
	public void start() {
		if (!isRunning()) {
			LOGGER.debug("Leader initiator starting");
			this.leaderRecordWatcher.start();
			this.hostPodWatcher.start();
			this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			this.scheduledExecutorService.scheduleAtFixedRate(this.leadershipController::update,
					this.leaderProperties.getUpdatePeriod().toMillis(),
					this.leaderProperties.getUpdatePeriod().toMillis(), TimeUnit.MILLISECONDS);
			this.isRunning = true;
		}
	}

	@Override
	public void stop() {
		if (isRunning()) {
			LOGGER.debug("Leader initiator stopping");
			this.scheduledExecutorService.shutdown();
			this.scheduledExecutorService = null;
			this.hostPodWatcher.stop();
			this.leaderRecordWatcher.stop();
			this.leadershipController.revoke();
			this.isRunning = false;
		}
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	@Override
	public boolean isRunning() {
		return this.isRunning;
	}

	@Override
	public int getPhase() {
		return 0;
	}

}
