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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
public final class PodReadyRunner {

	private final String candidateIdentity;

	private final String candidateNamespace;

	public PodReadyRunner(String candidateIdentity, String candidateNamespace) {
		this.candidateIdentity = candidateIdentity;
		this.candidateNamespace = candidateNamespace;
	}

	// how often the inner runnable runs, or how much is the scheduler kept alive
	private static final long TTL_MILLIS = 100;

	private static final LogAccessor LOG = new LogAccessor(PodReadyRunner.class);

	private final CachedSingleThreadScheduler podReadySelfShutDownScheduler = new CachedSingleThreadScheduler(
			"podReadyExecutor", TTL_MILLIS);

	public CompletableFuture<Void> podReady(BooleanSupplier podReadySupplier) {

		CompletableFuture<Void> podReadyCompletableFuture = new CompletableFuture<>();

		ScheduledFuture<?> scheduledFuture = podReadySelfShutDownScheduler.scheduleWithFixedDelay(() -> {

			if (podReadyCompletableFuture.isDone()) {
				LOG.info(() -> "pod readiness is known, not running another cycle");
				return;
			}

			try {
				if (podReadySupplier.getAsBoolean()) {
					LOG.info(
							() -> "Pod : " + candidateIdentity + " in namespace : " + candidateNamespace + " is ready");
					podReadyCompletableFuture.complete(null);
				}
				else {
					LOG.debug(() -> "Pod : " + candidateIdentity + " in namespace : " + candidateNamespace
							+ " is not ready, will retry in one second");
				}
			}
			catch (Exception e) {
				LOG.error(() -> "exception waiting for pod : " + candidateIdentity);
				LOG.error(() -> "pod readiness for : " + candidateIdentity + " failed with : " + e.getMessage());
				podReadyCompletableFuture.completeExceptionally(e);
			}

		}, 1, 1, TimeUnit.SECONDS);

		attachShutDownHook(podReadyCompletableFuture, scheduledFuture);

		return podReadyCompletableFuture;

	}

	/**
	 * call scheduledFuture::cancel, thus the podReadySelfShutDownScheduler will shutdown.
	 */
	private void attachShutDownHook(CompletableFuture<Void> podReadyCompletableFuture,
			ScheduledFuture<?> scheduledFuture) {

		podReadyCompletableFuture.whenComplete((ok, nok) -> {
			if (nok != null) {
				if (podReadyCompletableFuture.isCancelled()) {
					// something triggered us externally by calling
					// CompletableFuture::cancel,
					// need to shut down the readiness check
					LOG.debug(() -> "canceling scheduled future because completable future was cancelled");
				}
				else {
					LOG.debug(() -> "canceling scheduled future because readiness failed");
				}
			}
			else {
				LOG.debug(() -> "canceling scheduled future because readiness succeeded");
			}

			// no matter the outcome, we cancel the future and thus shut down the
			// executor that runs it.
			scheduledFuture.cancel(true);
		});
	}

}
