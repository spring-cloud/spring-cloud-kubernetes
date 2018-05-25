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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.springframework.integration.support.locks.ExpirableLockRegistry;
import org.springframework.util.Assert;

public class KubernetesLockRegistry implements ExpirableLockRegistry {

	private final ConfigMapLockRepository repository;

	private final String id;

	private final Map<String, KubernetesLock> locks;

	public KubernetesLockRegistry(ConfigMapLockRepository repository, String id) {
		this.repository = repository;
		this.id = id;
		this.locks = new HashMap<>();
	}

	@Override
	public Lock obtain(Object key) {
		Assert.isInstanceOf(String.class, key);
		String name = (String) key;

		return locks.computeIfAbsent(name, n -> new KubernetesLock(repository, n, id, System.currentTimeMillis()));
	}

	@Override
	public void expireUnusedOlderThan(long age) {
		locks.forEach((n, l) -> repository.deleteIfOlderThan(n, age));
	}

}
