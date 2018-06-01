/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
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

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.leader.Candidate;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeaderInitiator implements SmartLifecycle {

	private final Lock lock = new ReentrantLock();

	private final KubernetesClient kubernetesClient;

	private final Candidate candidate;

	private final LeaderProperties leaderProperties;

	private ScheduledExecutorService scheduledExecutorService;

	private String currentLeaderId; // TODO remove once events are implemented

	public LeaderInitiator(KubernetesClient kubernetesClient, Candidate candidate, LeaderProperties leaderProperties) {
		this.kubernetesClient = kubernetesClient;
		this.candidate = candidate;
		this.leaderProperties = leaderProperties;
	}

	@Override
	public boolean isAutoStartup() {
		return leaderProperties.isAutoStartup();
	}

	@Override
	public void start() {
		lock.lock();
		try {
			if (!isRunning()) {
				scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
				scheduledExecutorService.execute(this::update);
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void stop() {
		lock.lock();
		try {
			if (isRunning()) {
				scheduledExecutorService.shutdown();
				scheduledExecutorService = null;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void stop(Runnable runnable) {
		stop();
		runnable.run();
	}

	@Override
	public boolean isRunning() {
		return scheduledExecutorService != null;
	}

	@Override
	public int getPhase() {
		return 0; // TODO implement
	}

	public String getCurrentLeaderId() {
		return currentLeaderId;
	}

	private void update() {
		ConfigMap configMap = getConfigMap();
		Leader leader = getLeader(configMap);

		if (leader == null) {
			System.out.println("Currently there is no leader, trying to become one");
			try {
				takeLeadership(configMap);
				currentLeaderId = candidate.getId();
				scheduleUpdate(leaderProperties.getLeaseDuration());
			} catch (Exception e) {
				// Leadership takeover failed, try again later
				System.out.println("Leadership takeover failed: " + e.getMessage());
				scheduleUpdate(leaderProperties.getRetryPeriod());
			}
		} else if (!isValidLeader(leader)) {
			System.out.println("Old leader is not valid any more, try to take over");
			try {
				takeLeadership(configMap);
				currentLeaderId = candidate.getId();
				scheduleUpdate(leaderProperties.getLeaseDuration()); // Is this needed?
			} catch (Exception e) {
				// Leadership takeover failed, try again later
				System.out.println("Leadership takeover failed: " + e.getMessage());
				scheduleUpdate(leaderProperties.getRetryPeriod());
			}
		} else if (!isCandidateALeader(leader)) {
			currentLeaderId = leader.getId();
			System.out.println(currentLeaderId + " is a leader, check in later");
			scheduleUpdate(leaderProperties.getLeaseDuration());
		} else {
			System.out.println("I am a leader, check in later");
			currentLeaderId = candidate.getId();
			scheduleUpdate(leaderProperties.getLeaseDuration()); // Is this needed?
		}
	}

	private void takeLeadership(ConfigMap oldConfigMap) {
		String leaderIdKey = leaderProperties.getLeaderIdPrefix() + candidate.getRole();

		if (oldConfigMap == null) {
			ConfigMap newConfigMap = new ConfigMapBuilder().withNewMetadata()
				.withName(leaderProperties.getConfigMapName())
				.addToLabels("provider", "spring-cloud-kubernetes")
				.addToLabels("kind", "locks")
				.endMetadata()
				.addToData(leaderIdKey, candidate.getId())
				.build();

			kubernetesClient.configMaps()
				.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
				.create(newConfigMap);
		} else {
			ConfigMap newConfigMap = new ConfigMapBuilder(oldConfigMap)
				.addToData(leaderIdKey, candidate.getId())
				.build();

			kubernetesClient.configMaps()
				.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
				.withName(leaderProperties.getConfigMapName())
				.lockResourceVersion(oldConfigMap.getMetadata().getResourceVersion())
				.replace(newConfigMap);
		}

		System.out.println(candidate.getId() + " is now a leader");
	}

	private ConfigMap getConfigMap() {
		try {
			return kubernetesClient.configMaps()
				.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
				.withName(leaderProperties.getConfigMapName())
				.get();
		} catch (Exception e) {
			System.out.println("Failed to get a ConfigMap: " + e.getMessage());
			return null;
		}
	}

	private Leader getLeader(ConfigMap configMap) {
		if (configMap == null || configMap.getData() == null) {
			return null;
		}

		Map<String, String> data = configMap.getData();
		String leaderIdKey = leaderProperties.getLeaderIdPrefix() + candidate.getRole();
		String leaderId = data.get(leaderIdKey);
		if (leaderId == null) {
			return null;
		}

		return new Leader(candidate.getRole(), leaderId);
	}

	private void scheduleUpdate(long waitPeriod) {
		scheduledExecutorService.schedule(this::update, jitter(waitPeriod), TimeUnit.MILLISECONDS);
	}

	private long jitter(long num) {
		return (long) (num * (1 + Math.random() * (leaderProperties.getJitterFactor() - 1)));
	}

	private boolean isCandidateALeader(Leader leader) {
		return candidate.getId().equals(leader.getId());
	}

	private boolean isValidLeader(Leader leader) {
		return kubernetesClient.pods()
			.inNamespace(leaderProperties.getNamespace(kubernetesClient.getNamespace()))
			.withLabels(leaderProperties.getLabels())
			.list()
			.getItems()
			.stream()
			.map(Pod::getMetadata)
			.map(ObjectMeta::getName)
			.anyMatch(name -> name.equals(leader.getId()));
	}

}
