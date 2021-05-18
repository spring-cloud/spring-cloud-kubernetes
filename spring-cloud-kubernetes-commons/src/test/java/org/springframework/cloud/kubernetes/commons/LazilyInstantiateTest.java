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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LazilyInstantiateTest {

	private static final String TAG = "excluded-from-before";

	private static final String SINGLETON = "singleton";

	@Mock
	private Supplier<String> mockSupplier;

	@BeforeEach
	public void setUp(TestInfo testInfo) throws Exception {
		// some tests do not need this mocking
		if (!testInfo.getTags().contains(TAG)) {
			// common setup
			when(this.mockSupplier.get()).thenReturn(SINGLETON)
					.thenThrow(new RuntimeException("Supplier was called more than once!"));
		}
	}

	@Tag(TAG)
	@Test
	public void supplierNotCalledInLazyInstantiateFactoryMethod() {
		LazilyInstantiate.using(this.mockSupplier);

		// verify
		verifyNoInteractions(this.mockSupplier);
	}

	@Test
	public void factoryReturnsSingletonFromSupplier() {
		LazilyInstantiate<String> lazyStringFactory = LazilyInstantiate.using(this.mockSupplier);
		String singletonString = lazyStringFactory.get();

		// verify
		assertThat(singletonString).isEqualTo(SINGLETON);
	}

	@Test
	public void factoryOnlyCallsSupplierOnce() {
		LazilyInstantiate<String> lazyStringFactory = LazilyInstantiate.using(this.mockSupplier);
		lazyStringFactory.get();

		// mock will throw exception if it is called more than once
		lazyStringFactory.get();
	}

}
