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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author wind57
 */
@ExtendWith(MockitoExtension.class)
public class EventReloadDetectionModeTest {

	private static final String RELOAD_PROPERTY = "spring.cloud.kubernetes.reload.mode";

	private final EventReloadDetectionMode underTest = new EventReloadDetectionMode();

	@Mock
	private ConditionContext context;

	@Mock
	private Environment environment;

	@Mock
	private AnnotatedTypeMetadata metadata;

	// this is a weird test, since "containsProperty" returns "true", but "getProperty"
	// returns a "null".
	// I am leaving it here just to make sure nothing breaks in branching
	@Test
	public void testNull() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.containsProperty(RELOAD_PROPERTY)).thenReturn(true);
		Mockito.when(environment.getProperty(RELOAD_PROPERTY)).thenReturn(null);
		boolean matches = underTest.matches(context, metadata);
		Assertions.assertFalse(matches);
	}

	// lack of this property being set, means a match.
	@Test
	public void testDoesNotContain() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.containsProperty(RELOAD_PROPERTY)).thenReturn(false);
		boolean matches = underTest.matches(context, metadata);
		Assertions.assertTrue(matches);
	}

	@Test
	public void testMatchesCase() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.containsProperty(RELOAD_PROPERTY)).thenReturn(true);
		Mockito.when(environment.getProperty(RELOAD_PROPERTY)).thenReturn("EVENT");
		boolean matches = underTest.matches(context, metadata);
		Assertions.assertTrue(matches);
	}

	@Test
	public void testMatchesIgnoreCase() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.containsProperty(RELOAD_PROPERTY)).thenReturn(true);
		Mockito.when(environment.getProperty(RELOAD_PROPERTY)).thenReturn("eVeNt");
		boolean matches = underTest.matches(context, metadata);
		Assertions.assertTrue(matches);
	}

	@Test
	public void testNoMatch() {
		Mockito.when(context.getEnvironment()).thenReturn(environment);
		Mockito.when(environment.containsProperty(RELOAD_PROPERTY)).thenReturn(true);
		Mockito.when(environment.getProperty(RELOAD_PROPERTY)).thenReturn("not-eVeNt");
		boolean matches = underTest.matches(context, metadata);
		Assertions.assertFalse(matches);
	}

}
