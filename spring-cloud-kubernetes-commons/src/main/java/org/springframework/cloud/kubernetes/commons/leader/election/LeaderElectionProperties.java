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

package org.springframework.cloud.kubernetes.commons.leader.election;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @author wind57
 */
// @formatter:off
@ConfigurationProperties("spring.cloud.kubernetes.leader.election")
public record LeaderElectionProperties(
	@DefaultValue("true") boolean waitForPodReady,
	@DefaultValue("true") boolean publishEvents,
	@DefaultValue("15") int leaseDuration,
	@DefaultValue("default") String lockNamespace,
	@DefaultValue("spring-k8s-leader-election-lock") String lockName,
	@DefaultValue("10") int renewDeadline,
	@DefaultValue("2") int retryPeriod) {
// @formatter:on

	/**
	 * Coordination group for leader election.
	 */
	public static final String COORDINATION_GROUP = "coordination.k8s.io";

	/**
	 * Coordination version for leader election.
	 */
	public static final String COORDINATION_VERSION = "v1";

	/**
	 * Lease constant.
	 */
	public static final String LEASE = "Lease";

}
