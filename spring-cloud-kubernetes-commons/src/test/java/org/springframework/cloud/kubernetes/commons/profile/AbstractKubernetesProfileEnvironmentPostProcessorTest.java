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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

	private static final String PATH = "/some/path";

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

	@BeforeEach
	public void before() {
		paths = Mockito.mockStatic(Paths.class);
		files = Mockito.mockStatic(Files.class);
	}

	@AfterEach
	public void after() {
		paths.close();
		files.close();
	}

	/**
	 * <pre>
	 * 1) "spring.cloud.kubernetes.enabled" is false; thus nothing happens
	 * </pre>
	 */
	@Test
	public void testKubernetesDisabled() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=false");
		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfileNotPresent();
		assertKubernetesPropertySourceNotPresent();

	}

	/**
	 * <pre>
	 * 1) "spring.cloud.kubernetes.enabled" is true
	 * 2) "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is present, but does not resolve to an actual File
	 * </pre>
	 */
	@Test
	public void testKubernetesEnabledAndServiceAccountNamespacePathIsNotResolved() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.client.serviceAccountNamespacePath=" + PATH);
		serviceAccountFileResolved(false, PATH);
		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfilePresent();
		assertKubernetesPropertySourceNotPresent();

	}

	/**
	 * <pre>
	 * 1) "spring.cloud.kubernetes.enabled" is true
	 * 2) "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is present and resolves to an actual File
	 * </pre>
	 */
	@Test
	public void testKubernetesEnabledAndServiceAccountNamespacePathIsResolved() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.client.serviceAccountNamespacePath=" + PATH);

		Path path = serviceAccountFileResolved(true, PATH);
		mockServiceAccountNamespace(path);

		POST_PROCESSOR_INSIDE.postProcessEnvironment(context.getEnvironment(), springApplication);

		assertKubernetesProfilePresent();
		assertKubernetesPropertySourcePresent();

	}

	/**
	 * <pre>
	 * 1) "spring.cloud.kubernetes.enabled" is true
	 * 2) "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is not present, as such:
	 * 3) "/var/run/secrets/kubernetes.io/serviceaccount/namespace" is picked up, which is resolved and present
	 * </pre>
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

	/**
	 * <pre>
	 * 1) "spring.cloud.kubernetes.enabled" is true
	 * 2) isInsideKubernetes returns false
	 * 3) "spring.cloud.kubernetes.client.serviceAccountNamespacePath" is not present, as such:
	 * 4) "/var/run/secrets/kubernetes.io/serviceaccount/namespace" is picked up, which is resolved and present
	 * </pre>
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
		Assertions.assertFalse(kubernetesProfile().isPresent(),
				"'kubernetes' profile must not be present when 'spring.cloud.kubernetes.enabled' is false");
	}

	/*
	 * 'kubernetes' profile is present
	 */
	private void assertKubernetesProfilePresent() {
		Assertions.assertTrue(kubernetesProfile().isPresent(),
				"'kubernetes' profile must be present when 'spring.cloud.kubernetes.enabled' is true");
	}

	/*
	 * 'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source is not present
	 */
	private void assertKubernetesPropertySourceNotPresent() {
		Optional<PropertySource<?>> kubernetesPropertySource = kubernetesPropertySource();

		Assertions.assertFalse(kubernetesPropertySource.isPresent(),
				"'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source must not be present when 'spring.cloud.kubernetes.enabled' is false");
	}

	/*
	 * 'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source is present
	 */
	private void assertKubernetesPropertySourcePresent() {

		Optional<PropertySource<?>> kubernetesPropertySource = kubernetesPropertySource();
		Assertions.assertTrue(kubernetesPropertySource.isPresent(),
				"'KUBERNETES_NAMESPACE_PROPERTY_SOURCE' source must be present when 'spring.cloud.kubernetes.enabled' is true");

		String property = (String) kubernetesPropertySource.get()
				.getProperty("spring.cloud.kubernetes.client.namespace");
		Assertions.assertEquals(property, FOUNT_IT,
				"'spring.cloud.kubernetes.client.namespace' must be set to 'foundIt'");
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

	private Optional<String> kubernetesProfile() {
		return Arrays.stream(context.getEnvironment().getActiveProfiles()).filter("kubernetes"::equals).findFirst();
	}

	private Optional<PropertySource<?>> kubernetesPropertySource() {
		return context.getEnvironment().getPropertySources().stream()
				.filter(x -> "KUBERNETES_NAMESPACE_PROPERTY_SOURCE".equals(x.getName())).findAny();
	}

}
