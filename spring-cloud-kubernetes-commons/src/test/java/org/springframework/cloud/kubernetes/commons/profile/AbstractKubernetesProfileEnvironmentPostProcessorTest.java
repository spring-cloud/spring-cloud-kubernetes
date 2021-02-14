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

package org.springframework.cloud.kubernetes.commons.profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * @author wind57
 */
public class AbstractKubernetesProfileEnvironmentPostProcessorTest {

	private static final String FOUNT_IT = "foundIt";

	private MockedStatic<Paths> paths;

	private MockedStatic<Files> files;

	private final SpringApplication springApplication = Mockito.mock(SpringApplication.class);

	private final ConfigurableApplicationContext context = new AnnotationConfigApplicationContext();

	private static final AbstractKubernetesProfileEnvironmentPostProcessor POST_PROCESSOR_INSIDE = new AbstractKubernetesProfileEnvironmentPostProcessor() {
		@Override
		protected boolean isInsideKubernetes(Environment environment) {
			return true;
		}
	};

	private static final AbstractKubernetesProfileEnvironmentPostProcessor POST_PROCESSOR_OUTSIDE = new AbstractKubernetesProfileEnvironmentPostProcessor() {
		@Override
		protected boolean isInsideKubernetes(Environment environment) {
			return false;
		}
	};

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

	/*
	 * 1) "spring.cloud.kubernetes.enabled" is false; thus nothing happens
	 */
	@Test
	public void testKubernetesDisabled() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=false");
		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfileNotPresent();
		assertKubernetesPropertySourceNotPresent();

	}

	/*
	 * 1) "spring.cloud.kubernetes.enabled" is true 2)
	 * "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is present, but does
	 * not resolve to an actual File
	 */
	@Test
	public void testKubernetesEnabledAndServiceAccountNamespacePathIsNotResolved() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.client.serviceAccountNamespacePath=/some/path");
		serviceAccountFileResolved(false, "/some/path");
		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfilePresent();
		assertKubernetesPropertySourceNotPresent();

	}

	/*
	 * 1) "spring.cloud.kubernetes.enabled" is true 2)
	 * "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is present and
	 * resolves to an actual File
	 */
	@Test
	public void testKubernetesEnabledAndServiceAccountNamespacePathIsResolved() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.client.serviceAccountNamespacePath=/some/path");

		Path path = serviceAccountFileResolved(true, "/some/path");
		mockServiceAccountNamespace(path);

		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfilePresent();
		assertKubernetesPropertySourcePresent();

	}

	/*
	 * 1) "spring.cloud.kubernetes.enabled" is true 2)
	 * "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is not present, as
	 * such: 3) "/var/run/secrets/kubernetes.io/serviceaccount/namespace" is picked up,
	 * which is resolved and present
	 */
	@Test
	public void testKubernetesEnabledAndServiceAccountNamespacePathIsResolvedViaDefaultLocation() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true");

		Path path = serviceAccountFileResolved(true, "/var/run/secrets/kubernetes.io/serviceaccount/namespace");
		mockServiceAccountNamespace(path);

		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfilePresent();
		assertKubernetesPropertySourcePresent();
	}

	/*
	 * 1) "spring.cloud.kubernetes.enabled" is true 2) isInsideKubernetes returns false 3)
	 * "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is not present, as
	 * such: 4) "/var/run/secrets/kubernetes.io/serviceaccount/namespace" is picked up,
	 * which is resolved and present
	 */
	@Test
	public void testOutsideKubernetes() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true");

		Path path = serviceAccountFileResolved(true, "/var/run/secrets/kubernetes.io/serviceaccount/namespace");
		mockServiceAccountNamespace(path);

		POST_PROCESSOR_OUTSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfileNotPresent();
		assertKubernetesPropertySourcePresent();
	}

	/*
	 * 'kubernetes' profile is not present
	 */
	private void assertKubernetesProfileNotPresent() {
		long profile = Arrays.stream(context.getEnvironment().getActiveProfiles()).filter("kubernetes"::equals).count();
		Assert.assertEquals("'kubernetes' profile must not be present when 'spring.cloud.kubernetes.enabled' is false",
				0, profile);
	}

	/*
	 * 'kubernetes' profile is present
	 */
	private void assertKubernetesProfilePresent() {
		long profile = Arrays.stream(context.getEnvironment().getActiveProfiles()).filter("kubernetes"::equals).count();
		Assert.assertEquals("'kubernetes' profile must be present when 'spring.cloud.kubernetes.enabled' is true", 1,
				profile);
	}

	/*
	 * 'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source is not present
	 */
	private void assertKubernetesPropertySourceNotPresent() {
		Optional<PropertySource<?>> propertySource = context.getEnvironment().getPropertySources().stream()
			.filter(x -> "KUBERNETES_NAMESPACE_PROPERTY_SOURCE".equals(x.getName())).findAny();

		Assert.assertFalse(
				"'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source must not be present when 'spring.cloud.kubernetes.enabled' is false",
				propertySource.isPresent());
	}

	/*
	 * 'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source is present
	 */
	private void assertKubernetesPropertySourcePresent() {
		Optional<PropertySource<?>> propertySource = context.getEnvironment().getPropertySources().stream()
				.filter(x -> "KUBERNETES_NAMESPACE_PROPERTY_SOURCE".equals(x.getName())).findAny();

		Assert.assertTrue(
				"'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source must be present when 'spring.cloud.kubernetes.enabled' is true",
			propertySource.isPresent());

		String property = (String) propertySource.get().getProperty("spring.cloud.kubernetes.client.namespace");
		Assert.assertEquals(property, FOUNT_IT);
	}

	/*
	 * serviceAccountNamespace File is resolved or not
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
