/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
class KubernetesConfigDataLoaderTests {

	private static final ConfigDataLoaderContext CONTEXT = mock(ConfigDataLoaderContext.class);

	private static final DefaultBootstrapContext BOOTSTRAP_CONTEXT_NO_REGISTRATIONS = new DefaultBootstrapContext();

	private static final DefaultBootstrapContext BOOTSTRAP_CONTEXT_BOTH_REGISTRATIONS = new DefaultBootstrapContext();

	private static final Profiles PROFILES = mock(Profiles.class);

	private static final ConfigurableEnvironment ENVIRONMENT = new MockEnvironment();

	private static final KubernetesConfigDataResource EMPTY_RESOURCE = new KubernetesConfigDataResource(null, null,
			null, false, PROFILES, ENVIRONMENT);

	/**
	 * we do not override this method in our implementation, so it should report true for
	 * any arguments.
	 */
	@Test
	void testIsLoadable() {
		KubernetesConfigDataLoader loader = new KubernetesConfigDataLoader();
		Assertions.assertTrue(loader.isLoadable(null, null));
	}

	/**
	 * neither ConfigMapPropertySourceLocator nor SecretsPropertySourceLocator is
	 * registered in bootstrap context. There are no profiles either, as such
	 * PROFILE_SPECIFIC option is not present.
	 */
	@Test
	void testNeitherIsRegisteredNoProfiles() throws IOException {
		when(CONTEXT.getBootstrapContext()).thenReturn(BOOTSTRAP_CONTEXT_NO_REGISTRATIONS);
		KubernetesConfigDataLoader loader = new KubernetesConfigDataLoader();
		ConfigData configData = loader.load(CONTEXT, EMPTY_RESOURCE);

		MockPropertySource propertySource = new MockPropertySource("k8s");

		Assertions.assertNotNull(configData);
		Assertions.assertEquals(0, configData.getPropertySources().size());
		ConfigData.Options options = configData.getOptions(propertySource);
		Assertions.assertNotNull(options);
		Assertions.assertTrue(options.contains(ConfigData.Option.IGNORE_IMPORTS));
		Assertions.assertTrue(options.contains(ConfigData.Option.IGNORE_PROFILES));

		Assertions.assertFalse(options.contains(ConfigData.Option.PROFILE_SPECIFIC));
	}

	/**
	 * neither ConfigMapPropertySourceLocator nor SecretsPropertySourceLocator is
	 * registered in bootstrap context. "dev" profile is accepted, as such
	 * PROFILE_SPECIFIC option is present.
	 */
	@Test
	void testNeitherIsRegisteredDevProfilePresent() throws IOException {
		when(CONTEXT.getBootstrapContext()).thenReturn(BOOTSTRAP_CONTEXT_NO_REGISTRATIONS);
		when(PROFILES.getAccepted()).thenReturn(List.of("dev"));

		KubernetesConfigDataLoader loader = new KubernetesConfigDataLoader();
		ConfigData configData = loader.load(CONTEXT, EMPTY_RESOURCE);

		MockPropertySource propertySource = new MockPropertySource("k8s-dev");

		Assertions.assertNotNull(configData);
		Assertions.assertEquals(0, configData.getPropertySources().size());
		ConfigData.Options options = configData.getOptions(propertySource);
		Assertions.assertNotNull(options);
		Assertions.assertTrue(options.contains(ConfigData.Option.IGNORE_IMPORTS));
		Assertions.assertTrue(options.contains(ConfigData.Option.IGNORE_PROFILES));

		Assertions.assertTrue(options.contains(ConfigData.Option.PROFILE_SPECIFIC));
	}

	/**
	 * both ConfigMapPropertySourceLocator and SecretsPropertySourceLocator are registered
	 * in bootstrap context.
	 */
	@SuppressWarnings({ "raw", "unchecked" })
	@Test
	void testBothRegistered() throws IOException {

		PropertySource configMapPropertySource = new MockPropertySource("k8s-config-map");
		PropertySource secretsPropertySource = new MockPropertySource("k8s-secrets");
		ConfigMapPropertySourceLocator configMapPropertySourceLocator = mock(ConfigMapPropertySourceLocator.class);
		SecretsPropertySourceLocator secretsPropertySourceLocator = mock(SecretsPropertySourceLocator.class);
		when(CONTEXT.getBootstrapContext()).thenReturn(BOOTSTRAP_CONTEXT_BOTH_REGISTRATIONS);
		when(configMapPropertySourceLocator.locate(ENVIRONMENT)).thenReturn(configMapPropertySource);
		when(secretsPropertySourceLocator.locate(ENVIRONMENT)).thenReturn(secretsPropertySource);

		BOOTSTRAP_CONTEXT_BOTH_REGISTRATIONS.register(ConfigMapPropertySourceLocator.class,
				BootstrapRegistry.InstanceSupplier.of(configMapPropertySourceLocator));

		BOOTSTRAP_CONTEXT_BOTH_REGISTRATIONS.register(SecretsPropertySourceLocator.class,
				BootstrapRegistry.InstanceSupplier.of(secretsPropertySourceLocator));

		KubernetesConfigDataLoader loader = new KubernetesConfigDataLoader();
		ConfigData configData = loader.load(CONTEXT, EMPTY_RESOURCE);
		Assertions.assertNotNull(configData);
		Assertions.assertEquals(2, configData.getPropertySources().size());
		Assertions.assertEquals("k8s-secrets", configData.getPropertySources().get(0).getName());
		Assertions.assertEquals("k8s-config-map", configData.getPropertySources().get(1).getName());
	}

}
