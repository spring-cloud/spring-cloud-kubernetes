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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "logging.level.org.springframework.cloud.kubernetes.commons.leader.election=debug",
		"spring.cloud.config.enabled=false" })
@ExtendWith(OutputCaptureExtension.class)
class PodReadyRunnerTests {

	private final PodReadyRunner podReadyRunner = new PodReadyRunner("identity", "namespace");

	/**
	 * <pre>
	 *     - readiness passes from the first cycle
	 *     - assert that proper logging is in place
	 *     - assert that executor is getting shutdown
	 * </pre>
	 */
	@Test
	void readinessOKFromTheFirstCycle(CapturedOutput output) throws Exception {
		BooleanSupplier readinessSupplier = () -> true;
		CompletableFuture<Void> readinessFuture = podReadyRunner.podReady(readinessSupplier);
		readinessFuture.get();

		assertThat(output.getOut()).contains("Pod : identity in namespace : namespace is ready");
		assertThat(output.getOut()).contains("canceling scheduled future because readiness succeeded");

		await().atMost(Duration.ofSeconds(3))
			.pollInterval(Duration.ofMillis(200))
			.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));
	}

	/**
	 * <pre>
	 *     - readiness passes from the second cycle
	 * </pre>
	 */
	@Test
	void readinessOKFromTheSecondCycle(CapturedOutput output) throws Exception {
		AtomicInteger counter = new AtomicInteger(0);
		BooleanSupplier readinessSupplier = () -> {
			if (counter.get() == 0) {
				counter.incrementAndGet();
				return false;
			}
			return true;
		};
		CompletableFuture<Void> readinessFuture = podReadyRunner.podReady(readinessSupplier);
		readinessFuture.get();

		assertThat(output.getOut())
			.contains("Pod : identity in namespace : namespace is not ready, will retry in one second");
		assertThat(output.getOut()).contains("Pod : identity in namespace : namespace is ready");
		assertThat(output.getOut()).contains("canceling scheduled future because readiness succeeded");

		await().atMost(Duration.ofSeconds(3))
			.pollInterval(Duration.ofMillis(200))
			.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));
	}

	/**
	 * <pre>
	 *     - readiness throws an Exception in the second cycle
	 * </pre>
	 */
	@Test
	void readinessFailsOnTheSecondCycle(CapturedOutput output) {
		AtomicInteger counter = new AtomicInteger(0);
		BooleanSupplier readinessSupplier = () -> {
			if (counter.get() == 0) {
				counter.incrementAndGet();
				return false;
			}
			throw new RuntimeException("fail on the second cycle");
		};
		CompletableFuture<Void> readinessFuture = podReadyRunner.podReady(readinessSupplier);
		boolean caught = false;
		try {
			readinessFuture.get();
		}
		catch (Exception e) {
			caught = true;
			assertThat(output.getOut())
				.contains("Pod : identity in namespace : namespace is not ready, will retry in one second");
			assertThat(output.getOut()).contains("exception waiting for pod : identity");
			assertThat(output.getOut()).contains("pod readiness for : identity failed with : fail on the second cycle");
			assertThat(output.getOut()).contains("canceling scheduled future because readiness failed");

			await().atMost(Duration.ofSeconds(3))
				.pollInterval(Duration.ofMillis(200))
				.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));
		}
		assertThat(caught).isTrue();
	}

	/**
	 * <pre>
	 *     - readiness throws an Exception in the second cycle
	 *     - we chain one more thenApply and test it, just like
	 *       Fabric8LeaderElectionInitiator does it.
	 * </pre>
	 */
	@Test
	void readinessFailsOnTheSecondCycleAttachNewPipeline(CapturedOutput output) {
		AtomicInteger counter = new AtomicInteger(0);
		BooleanSupplier readinessSupplier = () -> {
			if (counter.get() == 0) {
				counter.incrementAndGet();
				return false;
			}
			throw new RuntimeException("fail on the second cycle");
		};
		CompletableFuture<Void> podReadyFuture = podReadyRunner.podReady(readinessSupplier);

		CompletableFuture<?> ready = podReadyFuture.whenComplete((ok, error) -> {
			if (error != null) {
				System.out.println("readiness failed and we caught that");
			}
			else {
				System.out.println("readiness succeeded");
			}
		});

		boolean caught = false;
		try {
			ready.get();
		}
		catch (Exception e) {
			caught = true;
			assertThat(output.getOut())
				.contains("Pod : identity in namespace : namespace is not ready, will retry in one second");
			assertThat(output.getOut()).contains("exception waiting for pod : identity");
			assertThat(output.getOut()).contains("pod readiness for : identity failed with : fail on the second cycle");
			assertThat(output.getOut()).contains("readiness failed and we caught that");
			assertThat(output.getOut()).contains("canceling scheduled future because readiness failed");

			await().atMost(Duration.ofSeconds(3))
				.pollInterval(Duration.ofMillis(200))
				.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));
		}
		assertThat(caught).isTrue();
	}

	/**
	 * <pre>
	 *     - readiness is canceled
	 *     - we chain one more thenApply and test it, just like
	 *       Fabric8LeaderElectionInitiator does it.
	 *
	 *     - this simulates when we issue cancel of the podReadyFuture from
	 *     - pre-destroy code.
	 * </pre>
	 */
	@Test
	void readinessCanceledOnTheSecondCycleAttachNewPipeline(CapturedOutput output) throws Exception {
		BooleanSupplier readinessSupplier = () -> false;

		CompletableFuture<Void> podReadyFuture = podReadyRunner.podReady(readinessSupplier);

		CompletableFuture<?> ready = podReadyFuture.whenComplete((ok, error) -> {
			if (error != null) {
				System.out.println("readiness failed and we caught that");
			}
			else {
				System.out.println("readiness succeeded");
			}
		});

		// sleep a few cycles of pod readiness check
		Thread.sleep(2_000);

		ScheduledExecutorService cancelScheduler = null;

		boolean caught = false;
		// cancel podReady future in a different thread
		cancelScheduler = Executors.newScheduledThreadPool(1);
		cancelScheduler.scheduleWithFixedDelay(() -> podReadyFuture.cancel(true), 1, 1, TimeUnit.SECONDS);

		try {
			ready.get();
		}
		catch (Exception e) {
			caught = true;
			assertThat(output.getOut())
				.contains("Pod : identity in namespace : namespace is not ready, will retry in one second");
			// this is a cancel of the future, not an exception per se
			assertThat(output.getOut()).doesNotContain("leader election for : identity was not successful");
			assertThat(output.getOut()).contains("readiness failed and we caught that");

			assertThat(output.getOut()).contains("canceling scheduled future because completable future was cancelled");
			assertThat(output.getOut()).doesNotContain("canceling scheduled future because readiness failed");

			await().atMost(Duration.ofSeconds(3))
				.pollInterval(Duration.ofMillis(200))
				.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));
		}
		assertThat(caught).isTrue();
		cancelScheduler.shutdownNow();

	}

}
