/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.ribbon;

import org.junit.Assert;
import org.junit.Test;

public class KubernetesConfigKeyTest {

	private class TypeOne<T> {
	}

	@Test
	public <T extends TypeOne, I> void testTypes() {
		// with class
		KubernetesConfigKey<String> key1 = new KubernetesConfigKey<String>("key1") {
		};

		// with type variable
		KubernetesConfigKey<T> key2 = new KubernetesConfigKey<T>("key2") {
		};

		// with type variable with no bounds
		KubernetesConfigKey<I> key3 = new KubernetesConfigKey<I>("key3") {
		};

		Assert.assertEquals(String.class, key1.type());
		Assert.assertEquals(TypeOne.class, key2.type());
		Assert.assertEquals(Object.class, key3.type());
	}

}
