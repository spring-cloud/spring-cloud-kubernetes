/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader.election;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.LEADER_ELECTION_ENABLED_PROPERTY;

/**
 * Leader election is enabled when either leader election is explicitly enabled or
 * Configuration Watcher HA is enabled.
 *
 * @author wind57
 */
final class LeaderElectionEnabledCondition extends SpringBootCondition {

	private static final String CONFIGURATION_WATCHER_HA_ENABLED_PROPERTY = "spring.cloud.kubernetes.configuration.watcher.ha.enabled";

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		boolean leaderElectionEnabled = context.getEnvironment()
			.getProperty(LEADER_ELECTION_ENABLED_PROPERTY, Boolean.class, false);

		boolean configurationWatcherHaEnabled = context.getEnvironment()
			.getProperty(CONFIGURATION_WATCHER_HA_ENABLED_PROPERTY, Boolean.class, false);

		if (leaderElectionEnabled || configurationWatcherHaEnabled) {
			return ConditionOutcome.match("Leader election is enabled");
		}

		return ConditionOutcome.noMatch("Leader election is disabled");
	}

}
