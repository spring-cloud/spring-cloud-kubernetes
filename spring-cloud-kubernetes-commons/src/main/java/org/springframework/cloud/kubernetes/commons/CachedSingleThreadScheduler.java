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

package org.springframework.cloud.kubernetes.commons;

import jakarta.annotation.Nonnull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is taken from fabric8 with some minor changes (we need it, so it could be placed
 * in the common package).
 *
 * @author wind57
 */
public final class CachedSingleThreadScheduler {

	private final ReentrantLock lock = new ReentrantLock();

	private final long ttlMillis;

	private ScheduledThreadPoolExecutor executor;

	public CachedSingleThreadScheduler(long ttlMillis) {
		this.ttlMillis = ttlMillis;
	}

	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		try {
			lock.lock();
			this.startExecutor();
			return this.executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
		}
		finally {
			lock.unlock();
		}
	}

	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		try {
			lock.lock();
			this.startExecutor();
			return this.executor.schedule(command, delay, unit);
		}
		finally {
			lock.unlock();
		}
	}

	private void startExecutor() {
		if (this.executor == null) {
			this.executor = new ScheduledThreadPoolExecutor(1, threadFactory());
			this.executor.setRemoveOnCancelPolicy(true);
			this.executor.scheduleWithFixedDelay(this::shutdownCheck, this.ttlMillis, this.ttlMillis,
				TimeUnit.MILLISECONDS);
		}

	}

	private void shutdownCheck() {
		try {
			lock.lock();
			if (this.executor.getQueue().isEmpty()) {
				this.executor.shutdownNow();
				this.executor = null;
			}
		}
		finally {
			lock.unlock();
		}

	}

	private ThreadFactory threadFactory() {
		return new ThreadFactory() {
			final ThreadFactory threadFactory = Executors.defaultThreadFactory();

			@Override
			public Thread newThread(@Nonnull Runnable runnable) {
				Thread thread = threadFactory.newThread(runnable);
				thread.setName("fabric8-leader-election" + "-" + thread.getName());
				thread.setDaemon(true);
				return thread;
			}
		};
	}

}
