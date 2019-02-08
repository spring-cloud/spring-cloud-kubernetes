/*
 * Copyright 2013-2019 the original author or authors.
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
 */

package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import static org.mockito.MockitoAnnotations.initMocks;

public class ConfigMapPropertySourceLocatorTest {

	@Mock
	private KubernetesClient mockClient;

	@Mock
	private ConfigMapConfigProperties mockProperties;

	@Mock
	private ConfigurableEnvironment configurableEnvironment;

	private ConfigMapPropertySourceLocator configMapPropertySourceLocatorUnderTest;

	@Before
	public void setUp() {
		initMocks(this);
		configMapPropertySourceLocatorUnderTest = new ConfigMapPropertySourceLocator(
				mockClient, mockProperties);
	}

	@Test
	public void testRecursivePathTraverse() {
		Mockito.when(mockProperties.getPaths())
				.thenReturn(Lists.newArrayList("src/test/resources/test-paths"));

		final PropertySource result = configMapPropertySourceLocatorUnderTest
				.locate(configurableEnvironment);

		Assert.assertNotNull(result);
		Assert.assertEquals("2", result.getProperty("test.property2"));
		Assert.assertEquals("1", result.getProperty("test.property1"));
	}

}
