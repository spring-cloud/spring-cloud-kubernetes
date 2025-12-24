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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
public final class LeaderElectionInitiatorUtil {

	private static final LogAccessor LOG = new LogAccessor(LeaderElectionInitiatorUtil.class);

	private LeaderElectionInitiatorUtil() {

	}

	public static void blockReadinessCheck(CompletableFuture<?> ready) {
		try {
			ready.get();
		}
		catch (Exception e) {
			LOG.error(e, () -> "block readiness check failed with : " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	public static void shutDownExecutor(ExecutorService podReadyWaitingExecutor, String candidateIdentity) {
		LOG.debug(() -> "podReadyWaitingExecutor will be shutdown for : " + candidateIdentity);
		podReadyWaitingExecutor.shutdownNow();
		try {
			podReadyWaitingExecutor.awaitTermination(3, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * if 'ready' is already completed at this point, thread will run this, otherwise it
	 * will attach the pipeline and move on to 'blockReadinessCheck'.
	 */
	public static CompletableFuture<?> attachReadinessLoggerPipeline(CompletableFuture<?> innerPodReadyFuture,
			String candidateIdentity) {
		return innerPodReadyFuture.whenComplete((ok, error) -> {
			if (error != null) {
				LOG.error(() -> "readiness failed for : " + candidateIdentity + ", leader election will not start");
			}
			else {
				LOG.info(() -> candidateIdentity + " is ready");
			}
		});
	}

	public static void sleep(LeaderElectionProperties leaderElectionProperties) {
		try {
			TimeUnit.SECONDS.sleep(leaderElectionProperties.waitAfterRenewalFailure().toSeconds());
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
