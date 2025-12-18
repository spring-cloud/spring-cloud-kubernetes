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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.LEADER_ELECTION_PROPERTY_PREFIX;

/**
 * <pre>
 * waitForPodReady: should we wait for the readiness of the pod,
 *      before we even trigger the leader election process.
 * publishEvents: should we publish events (ApplicationEvent)
 *      when the state of leaders changes.
 * leaseDuration: TTL of the lease. No other leader candidate
 *      can acquire the lease unless this one expires.
 * lockNamespace: where to create the "lock"
 *      (this is either a lease or a config map)
 * lockName: the name of the lease or configmap
 * renewDeadline: once the lock is acquired,
 *      and we are the current leader, we try to "extend" the lease.
 *      We must extend it within this timeline.
 * retryPeriod: how often to retry when trying to get
 *      the lock to become the leader. In our current code,
 *      this is what we use in LeaderInitiator::start,
 *      more exactly in the scheduleAtFixRate
 * restartOnFailure: what to do when leader election future fails
 *      with an Exception. Do we restart the leader election process
 *      or let it fail and thus this instance never participates
 *      in the leader election process.
 *
 *
 * First, we try to acquire the lock (lock is either a configmap or a lease)
 * and by "acquire" I mean write to it (or its annotations for a configmap).
 * Whoever writes first (all others will get a 409) becomes the leader.
 * All leader candidates that are not leaders will continue to spin forever
 * until they get a chance to become one. They retry every 'retryPeriod'.
 * The current leader, after it establishes itself as one,
 * will spin forever too, but will try to extend its leadership.
 * It extends that by updating the entries in the lease,
 * specifically the one we care about is: renewTime.
 * This one is updated every 'retryPeriod'. For example,
 * every 2 seconds (retryPeriod), it will update its 'renewTime' with "now".
 *
 * All other, non-leaders are spinning and check a few things in each cycle:
 * "Am I the leader?" If the answer is no, they go below:
 * "Can I become the leader?" This is answered by looking at:
 * now().isAfter(leaderElectionRecord.getRenewTime()
 *           .plus(leaderElectionConfig.getLeaseDuration()))
 * So they can only try to acquire the leadership if 'leaseDuration'
 * (basically a TTL) + renewTime (when was the last renewal) has expired.
 * This means that no one will be able to even try to acquire the lock
 * until that leaseDuration expires. When the pod is killed or dies
 * unexpectedly (OOM, for example), all non-leaders will wait until
 * leaseDuration expires.
 *
 * In case of a graceful shutdown (we call CompletableFuture::cancel on the fabric8 instances),
 * there is code that fabric8 will trigger to "reset" the lease:
 * they will set the renewTime to "now" and leaseDuration to 1 second.
 * </pre>
 *
 * @author wind57
 */
// @formatter:off
@ConfigurationProperties(LEADER_ELECTION_PROPERTY_PREFIX)
public record LeaderElectionProperties(
	@DefaultValue("true") boolean waitForPodReady,
	@DefaultValue("true") boolean publishEvents,
	@DefaultValue("15s") Duration leaseDuration,
	@DefaultValue("default") String lockNamespace,
	@DefaultValue("spring-k8s-leader-election-lock") String lockName,
	@DefaultValue("10s") Duration renewDeadline,
	@DefaultValue("2s") Duration retryPeriod,
	@DefaultValue("3s") Duration waitAfterRenewalFailure,
	@DefaultValue("false") boolean useConfigMapAsLock,
	@DefaultValue("true") boolean restartOnFailure) {
// @formatter:on
}
