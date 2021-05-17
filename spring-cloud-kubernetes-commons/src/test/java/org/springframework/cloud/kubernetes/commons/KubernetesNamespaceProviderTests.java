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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider.NAMESPACE_PROPERTY;

/**
 * @author Ryan Baxter
 */
public class KubernetesNamespaceProviderTests {

	private static final String PATH = "/some/path";

	private static final String FOUNT_IT = "foundIt";

	private MockedStatic<Paths> paths;

	private MockedStatic<Files> files;

	@Before
	public void before() {
		paths = Mockito.mockStatic(Paths.class);
		files = Mockito.mockStatic(Files.class);
	}

	@After
	public void after() {
		paths.close();
		files.close();
	}

	@Test
	public void getNamespace() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(NAMESPACE_PROPERTY, "mynamespace");
		KubernetesNamespaceProvider p1 = new KubernetesNamespaceProvider(environment);
		assertThat(p1.getNamespace()).isEqualTo("mynamespace");
		paths.verify(times(0), () -> Paths.get(PATH));

	}

	/**
	 * <pre>
	 * 1) serviceAccountNamespace File is present or not
	 * 2) if the above is present, under what actualPath
	 * </pre>
	 */
	private Path serviceAccountFileResolved(boolean present, String actualPath) {
		Path path = Mockito.mock(Path.class);
		paths.when(() -> Paths.get(actualPath)).thenReturn(path);
		files.when(() -> Files.isRegularFile(path)).thenReturn(present);
		return path;
	}

	/*
	 * returns "foundIt" for service account namespace
	 */
	private void mockServiceAccountNamespace(Path path) {
		files.when(() -> Files.readAllBytes(path)).thenReturn(FOUNT_IT.getBytes());
	}

}
