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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaderElectionRecord;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;

/**
 * Wraps a {@link LeaseLock} to work around a fabric8 client regression: since 7.5.0,
 * {@code LeaderElector.release()} (invoked on cancel, when {@code withReleaseOnCancel()}
 * is configured) builds the released {@link LeaderElectionRecord} with a {@code null}
 * holder identity instead of the previous {@code ""}. That change was intentional (it
 * fixes a client-side callback-duplication bug, see
 * <a href="https://github.com/fabric8io/kubernetes-client/issues/7343">issue 7343</a>),
 * but it has a side effect for leases specifically: {@code LeaseSpec} is annotated
 * {@code @JsonInclude(Include.NON_NULL)}, so when {@link Lock#update} applies the record
 * as a {@code JSON_MERGE} patch, the {@code null} holder identity is omitted from the
 * patch body entirely. An omitted field means "no change", so the holder identity is
 * never actually cleared on the server - {@code release()} reports success, but the lease
 * still looks held.
 *
 * <p>
 * Simply coercing the {@code null} back to {@code ""} is not enough either: a literal
 * empty string would genuinely be written and persisted, and the next time a fresh
 * {@code LeaderElector} reads that lease (e.g. after a restart), comparing {@code ""}
 * against its own initial {@code null} observed state reintroduces the exact spurious
 * "new leader" callback that issue 7343 fixed. Instead, this decorator follows up the
 * normal update with an explicit raw JSON {@code null} merge patch for just the holder
 * identity field, so it is genuinely removed from the resource (not omitted, and not left
 * as {@code ""}) - a fresh read then correctly sees {@code null}.
 *
 * <p>
 * {@code ConfigMapLock} is not affected and is left untouched: it serializes the whole
 * {@link LeaderElectionRecord} as a single annotation value, and
 * {@link LeaderElectionRecord} itself has no {@code @JsonInclude(NON_NULL)}, so a
 * {@code null} holder identity is included as-is.
 *
 * <p>
 * This class can be removed once the bug is fixed upstream.
 *
 * @author ryanjbaxter
 * @see <a href=
 * "https://github.com/fabric8io/kubernetes-client/issues/7343">fabric8io/kubernetes-client#7343</a>
 */
final class Fabric8ReleaseOnCancelLock implements Lock {

	private final LeaseLock delegate;

	private final String namespace;

	private final String name;

	Fabric8ReleaseOnCancelLock(LeaseLock delegate, String namespace, String name) {
		this.delegate = delegate;
		this.namespace = namespace;
		this.name = name;
	}

	@Override
	public LeaderElectionRecord get(KubernetesClient client) {
		return delegate.get(client);
	}

	@Override
	public void create(KubernetesClient client, LeaderElectionRecord leaderElectionRecord) {
		delegate.create(client, leaderElectionRecord);
	}

	@Override
	public void update(KubernetesClient client, LeaderElectionRecord leaderElectionRecord) {
		delegate.update(client, leaderElectionRecord);
		if (leaderElectionRecord.getHolderIdentity() == null) {
			client.leases()
				.inNamespace(namespace)
				.withName(name)
				.patch(PatchContext.of(PatchType.JSON_MERGE), "{\"spec\":{\"holderIdentity\":null}}");
		}
	}

	@Override
	public String identity() {
		return delegate.identity();
	}

	@Override
	public String describe() {
		return delegate.describe();
	}

}
