/*
 *     Copyright (C) 2018 to the original authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.cloud.kubernetes.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class KubernetesLock implements Lock {

	private static final int LOCK_RETRY_INTERVAL = 100;

	private final ConfigMapLockRepository repository;

	private final String name;

	private final String holder;

	private final long expiration;

	public KubernetesLock(ConfigMapLockRepository repository, String name, String holder, long expiration) {
		this.repository = repository;
		this.name = name;
		this.holder = holder;
		this.expiration = expiration;
	}

	@Override
	public void lock() {
		while (true) {
			try {
				if (tryLock()) {
					return;
				}
				Thread.sleep(LOCK_RETRY_INTERVAL);
			} catch (InterruptedException e) {
				// This method cannot be interrupted
			}
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		while (true) {
			if (tryLock()) {
				return;
			}
			Thread.sleep(LOCK_RETRY_INTERVAL);
		}
	}

	@Override
	public boolean tryLock() {
		repository.deleteIfExpired(name);
		return repository.create(name, holder, expiration);
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		long expiration = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(time, unit);
		while (true) {
			if (System.currentTimeMillis() > expiration) {
				return false;
			} else if (tryLock()) {
				return true;
			}
			Thread.sleep(LOCK_RETRY_INTERVAL);
		}
	}

	@Override
	public void unlock() {
		repository.delete(name);
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException("Condition is not supported");
	}

}
