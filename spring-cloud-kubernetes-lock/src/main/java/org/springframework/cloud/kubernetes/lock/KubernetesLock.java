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

	private static final int RETRY_PERIOD = 100;

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
		repository.deleteIfExpired(name);
		while (true) {
			try {
				if (repository.create(name, holder, expiration)) {
					return;
				}
				Thread.sleep(RETRY_PERIOD);
			} catch (InterruptedException e) {
				// This method cannot be interrupted
			}
 		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		// TODO same as lock(), but thread can be interrupted
	}

	@Override
	public boolean tryLock() {
		// TODO same as lock() but return immediately if te lock cannot be acquired
		return false;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		// TODO same as lockInterruptibly() but return after specified time if te lock cannot be acquired.
		return false;
	}

	@Override
	public void unlock() {
   		// TODO delete configmap for this lock
		// TODO consider only allowing lock release only from the same application and/or thread
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException("Condition is not supported");
	}

}
