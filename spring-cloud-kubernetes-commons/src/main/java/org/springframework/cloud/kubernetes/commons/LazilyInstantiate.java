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

package org.springframework.cloud.kubernetes.commons;

import java.util.function.Supplier;

/**
 * Lazy instantiation utility class.
 *
 * @param <T> return type
 * @author Ioannis Canellos
 */
public final class LazilyInstantiate<T> implements Supplier<T> {

	private final Supplier<T> supplier;

	private Supplier<T> current;

	private LazilyInstantiate(Supplier<T> supplier) {
		this.supplier = supplier;
		this.current = () -> swapper();
	}

	public static <T> LazilyInstantiate<T> using(Supplier<T> supplier) {
		return new LazilyInstantiate<T>(supplier);
	}

	public synchronized T get() {
		return this.current.get();
	}

	private T swapper() {
		T obj = this.supplier.get();
		this.current = () -> obj;
		return obj;
	}

}
