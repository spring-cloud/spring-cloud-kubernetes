/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config.reload.condition;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author wind57
 */
@RunWith(MockitoJUnitRunner.class)
public class EventReloadDetectionModeTest {

	private final EventReloadDetectionMode underTest = new EventReloadDetectionMode();

	@Mock
	private ConditionContext context;

	@Mock
	private Environment environment;

	@Mock
	private AnnotatedTypeMetadata metadata;

	@Test
	public void testNull() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.getProperty("spring.cloud.kubernetes.reload.mode")).thenReturn(null);
		boolean matches = underTest.matches(context, metadata);
		Assert.assertFalse(matches);
	}

	@Test
	public void testMatchesCase() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.getProperty("spring.cloud.kubernetes.reload.mode")).thenReturn("EVENT");
		boolean matches = underTest.matches(context, metadata);
		Assert.assertTrue(matches);
	}

	@Test
	public void testMatchesIgnoreCase() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.getProperty("spring.cloud.kubernetes.reload.mode")).thenReturn("eVeNt");
		boolean matches = underTest.matches(context, metadata);
		Assert.assertTrue(matches);
	}

}
