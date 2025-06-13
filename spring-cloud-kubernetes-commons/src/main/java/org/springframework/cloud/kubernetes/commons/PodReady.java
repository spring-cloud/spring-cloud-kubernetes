/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons;

import org.springframework.core.log.LogAccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * @author wind57
 */
public final class PodReady {

	private static final LogAccessor LOG = new LogAccessor(PodReady.class);

	private final CachedSingleThreadScheduler podReadyScheduler = new CachedSingleThreadScheduler(
		TimeUnit.SECONDS.toMillis(10)
	);

	public CompletableFuture<Void> podReady(BooleanSupplier isPodReady, String holderIdentity, String podNamespace) {

		CompletableFuture<Void> podReadyFuture = new CompletableFuture<>();

		podReadyScheduler.scheduleWithFixedDelay(() -> {

			try {
				LOG.info(() -> "waiting for pod : " + holderIdentity + " in namespace : " + podNamespace
					+ " to be ready");
				if (isPodReady.getAsBoolean()) {
					LOG.info(() -> "Pod : " + holderIdentity + " in namespace : " + podNamespace + " is ready");
					podReadyFuture.complete(null);
				}
				else {
					LOG.debug(() -> "Pod : " + holderIdentity + " in namespace : " + podNamespace
						+ " is not ready, " + "will retry in one second");
				}
			}
			catch (Exception e) {
				LOG.error(() -> "exception waiting for pod : " + e.getMessage());
				LOG.error(() -> "leader election for " + holderIdentity + "  was not successful");
				podReadyFuture.completeExceptionally(e);
			}

		}, 1, 1, TimeUnit.SECONDS);

		return podReadyFuture;

	}

}
