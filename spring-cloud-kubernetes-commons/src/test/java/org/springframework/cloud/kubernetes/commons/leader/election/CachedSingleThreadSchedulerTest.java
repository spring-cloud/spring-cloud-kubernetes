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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = { "spring.cloud.config.enabled=false",
		"logging.level.org.springframework.cloud.kubernetes.commons.leader.election=debug" })
class CachedSingleThreadSchedulerTest {

	/**
	 * <pre>
	 *     - pod readiness passes after two attempts
	 *     - we check that the executor is shutdown after readiness passes
	 * </pre>
	 */
	@Test
	void readinessPasses(CapturedOutput output) throws Exception {

		AtomicInteger counter = new AtomicInteger();

		BooleanSupplier supplier = () -> {
			if (counter.get() == 2) {
				return true;
			}
			else {
				counter.incrementAndGet();
			}
			return false;
		};

		PodReadyRunner readyRunner = new PodReadyRunner("my-pod", "my-namespace");
		CompletableFuture<Void> ready = readyRunner.podReady(supplier);
		ready.get();

		String out = output.getOut();
		Assertions.assertThat(out).contains("Scheduling command to run in : podReadyExecutor");
		Assertions.assertThat(out)
			.contains("Pod : my-pod in namespace : " + "my-namespace is not ready, will retry in one second");
		Assertions.assertThat(out).contains("Pod : my-pod in namespace : " + "my-namespace is ready");

		// executor is shutting down
		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));

		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("canceling scheduled future because readiness succeeded"));
	}

	/**
	 * <pre>
	 *     - pod readiness fails after one attempt
	 *     - we check that the executor is shutdown after that
	 * </pre>
	 */
	@Test
	void readinessFails(CapturedOutput output) throws Exception {

		AtomicInteger counter = new AtomicInteger();

		BooleanSupplier supplier = () -> {
			if (counter.get() == 1) {
				throw new RuntimeException("just because");
			}
			else {
				counter.incrementAndGet();
			}
			return false;
		};

		PodReadyRunner readyRunner = new PodReadyRunner("my-pod", "my-namespace");
		CompletableFuture<Void> ready = readyRunner.podReady(supplier);

		ExecutorService readyCheckExecutor = Executors.newSingleThreadExecutor();

		boolean[] caught = new boolean[1];
		// just like Fabric8LeaderElectionInitiator does it
		// ready.get is called in a different executor
		readyCheckExecutor.submit(() -> {
			try {
				ready.get();
			}
			catch (Exception e) {
				caught[0] = true;
				throw new RuntimeException(e);
			}
		});

		// pod readiness is started
		Awaitility.await()
			.pollInterval(Duration.ofMillis(200))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("Scheduling command to run in : podReadyExecutor"));

		// pod readiness progresses
		Awaitility.await()
			.pollInterval(Duration.ofMillis(200))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut()
				.contains("Pod : my-pod in namespace : " + "my-namespace is not ready, will retry in one second"));

		// executor is shutting down
		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));

		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("canceling scheduled future because readiness failed"));

		Assertions.assertThat(caught[0]).isTrue();
	}

	/**
	 * <pre>
	 *     - pod readiness is not established
	 *     - we cancel the future
	 *     - we check that the executor is shutdown after that
	 * </pre>
	 */
	@Test
	void readinessCanceled(CapturedOutput output) throws Exception {

		BooleanSupplier supplier = () -> false;

		PodReadyRunner readyRunner = new PodReadyRunner("my-pod", "my-namespace");
		CompletableFuture<Void> ready = readyRunner.podReady(supplier);

		// sleep a few cycles of pod readiness check
		Thread.sleep(2_000);

		// cancel must end the readiness check
		ready.cancel(true);

		// pod readiness is started
		Awaitility.await()
			.pollInterval(Duration.ofMillis(200))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("Scheduling command to run in : podReadyExecutor"));

		// pod readiness progresses
		Awaitility.await()
			.pollInterval(Duration.ofMillis(200))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut()
				.contains("Pod : my-pod in namespace : " + "my-namespace is not ready, will retry in one second"));

		// executor is shutting down
		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut().contains("Shutting down executor : podReadyExecutor"));

		Awaitility.await()
			.pollInterval(Duration.ofMillis(1))
			.atMost(Duration.ofSeconds(10))
			.until(() -> output.getOut()
				.contains("canceling scheduled future because completable future was cancelled"));

	}

}
