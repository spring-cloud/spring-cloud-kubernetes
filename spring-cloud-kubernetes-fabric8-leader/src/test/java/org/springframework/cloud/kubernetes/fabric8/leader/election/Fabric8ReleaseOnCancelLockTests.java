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

import java.time.Duration;
import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaderElectionRecord;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * @author ryanjbaxter
 */
@SuppressWarnings("unchecked")
class Fabric8ReleaseOnCancelLockTests {

	private static final String NAMESPACE = "ns";

	private static final String NAME = "lock-name";

	private final LeaseLock delegate = Mockito.mock(LeaseLock.class);

	private final KubernetesClient client = Mockito.mock(KubernetesClient.class);

	private final MixedOperation<Lease, LeaseList, Resource<Lease>> leases = Mockito.mock(MixedOperation.class);

	private final NonNamespaceOperation<Lease, LeaseList, Resource<Lease>> namespacedLeases = Mockito
		.mock(NonNamespaceOperation.class);

	private final Resource<Lease> leaseResource = Mockito.mock(Resource.class);

	private final Fabric8ReleaseOnCancelLock lock = new Fabric8ReleaseOnCancelLock(delegate, NAMESPACE, NAME);

	@BeforeEach
	void setUp() {
		Mockito.when(client.leases()).thenReturn(leases);
		Mockito.when(leases.inNamespace(NAMESPACE)).thenReturn(namespacedLeases);
		Mockito.when(namespacedLeases.withName(NAME)).thenReturn(leaseResource);
	}

	/**
	 * fabric8 7.5.x LeaderElector.release() builds a record with a null holder identity,
	 * which gets silently omitted from the JSON_MERGE patch body (since LeaseSpec
	 * is @JsonInclude(NON_NULL)) and never clears the field server-side. A follow-up raw
	 * JSON null merge patch must be issued to actually clear it.
	 */
	@Test
	void nullHolderIdentityTriggersFollowUpRawNullPatch() {
		ZonedDateTime now = ZonedDateTime.now();
		LeaderElectionRecord withNullHolder = new LeaderElectionRecord(null, Duration.ofSeconds(1), now, now, 0);

		lock.update(client, withNullHolder);

		ArgumentCaptor<LeaderElectionRecord> captor = ArgumentCaptor.forClass(LeaderElectionRecord.class);
		Mockito.verify(delegate).update(Mockito.eq(client), captor.capture());
		assertThat(captor.getValue()).isSameAs(withNullHolder);

		ArgumentCaptor<PatchContext> patchContextCaptor = ArgumentCaptor.forClass(PatchContext.class);
		Mockito.verify(leaseResource).patch(patchContextCaptor.capture(), eq("{\"spec\":{\"holderIdentity\":null}}"));
		assertThat(patchContextCaptor.getValue().getPatchType()).isEqualTo(PatchType.JSON_MERGE);
	}

	/**
	 * a non-null holder identity must be passed through unchanged, with no follow-up
	 * patch issued.
	 */
	@Test
	void nonNullHolderIdentityIsUntouched() {
		ZonedDateTime now = ZonedDateTime.now();
		LeaderElectionRecord withHolder = new LeaderElectionRecord("leader-1", Duration.ofSeconds(1), now, now, 0);

		lock.update(client, withHolder);

		Mockito.verify(delegate).update(client, withHolder);
		Mockito.verify(leaseResource, Mockito.never())
			.patch(Mockito.any(PatchContext.class), Mockito.any(String.class));
	}

}
