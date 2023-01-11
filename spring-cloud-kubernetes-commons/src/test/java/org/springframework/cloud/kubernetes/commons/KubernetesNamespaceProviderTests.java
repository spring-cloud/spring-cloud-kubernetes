/*
 * Copyright 2013-2021 the original author or authors.
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

import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;

/**
 * @author Ryan Baxter
 */
class KubernetesNamespaceProviderTests {

	private static final String PATH = "/some/path";

	private MockedStatic<Paths> paths;

	@BeforeEach
	void before() {
		paths = Mockito.mockStatic(Paths.class);
	}

	@AfterEach
	void after() {
		paths.close();
	}

	@Test
	void getNamespace() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(NAMESPACE_PROPERTY, "mynamespace");
		KubernetesNamespaceProvider p1 = new KubernetesNamespaceProvider(environment);
		assertThat(p1.getNamespace()).isEqualTo("mynamespace");
		paths.verify(() -> Paths.get(PATH), times(0));

	}

}
