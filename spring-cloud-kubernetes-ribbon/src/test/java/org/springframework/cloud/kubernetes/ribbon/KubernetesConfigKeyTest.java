/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.ribbon;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesConfigKeyTest {

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

		assertThat(key1.type()).isEqualTo(String.class);
		assertThat(key2.type()).isEqualTo(TypeOne.class);
		assertThat(key3.type()).isEqualTo(Object.class);
	}

	private class TypeOne<T> {

	}

}
