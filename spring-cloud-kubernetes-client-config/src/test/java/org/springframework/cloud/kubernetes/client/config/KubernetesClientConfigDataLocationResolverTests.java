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

package org.springframework.cloud.kubernetes.client.config;

import java.util.function.Supplier;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigDataRetryableConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.ConfigDataRetryableSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class KubernetesClientConfigDataLocationResolverTests {

	private static final DeferredLogFactory FACTORY = Supplier::get;

	private static final ConfigDataLocationResolverContext RESOLVER_CONTEXT = Mockito
			.mock(ConfigDataLocationResolverContext.class);

	private static final KubernetesClientConfigDataLocationResolver RESOLVER = new KubernetesClientConfigDataLocationResolver(
			FACTORY);

	/*
	 * both ConfigMapConfigProperties and SecretsConfigProperties are null, thus they are
	 * not registered. It also means that ConfigMapPropertySourceLocator and
	 * SecretsPropertySourceLocator are not registered either.
	 */
	@Test
	void testBothMissing() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.config.enabled", "false");
		environment.setProperty("spring.cloud.kubernetes.secrets.enabled", "false");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertTrue(context.isRegistered(KubernetesClientProperties.class));
		Assertions.assertTrue(context.isRegistered(CoreV1Api.class));
		Assertions.assertTrue(context.isRegistered(ApiClient.class));

		Assertions.assertFalse(context.isRegistered(ConfigMapConfigProperties.class));
		Assertions.assertFalse(context.isRegistered(SecretsConfigProperties.class));

		Assertions.assertFalse(context.isRegistered(ConfigMapPropertySourceLocator.class));
		Assertions.assertFalse(context.isRegistered(SecretsPropertySourceLocator.class));
	}

	/*
	 * both ConfigMapConfigProperties and SecretsConfigProperties are enabled
	 * (via @Default on 'spring.cloud.kubernetes.config.enabled' and
	 * 'spring.cloud.kubernetes.secrets.enabled'); as such they are both registered.
	 *
	 * It also means that ConfigMapPropertySourceLocator and SecretsPropertySourceLocator
	 * are registered too.
	 *
	 * Since retry is not enabled explicitly, we also assert the types to ensure that
	 * these are not retryable beans.
	 */
	@Test
	void testBothPresent() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertTrue(context.isRegistered(KubernetesClientProperties.class));
		Assertions.assertTrue(context.isRegistered(CoreV1Api.class));
		Assertions.assertTrue(context.isRegistered(ApiClient.class));

		Assertions.assertTrue(context.isRegistered(ConfigMapConfigProperties.class));
		Assertions.assertTrue(context.isRegistered(SecretsConfigProperties.class));

		Assertions.assertTrue(context.isRegistered(ConfigMapPropertySourceLocator.class));
		Assertions.assertTrue(context.isRegistered(SecretsPropertySourceLocator.class));

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
				.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertSame(KubernetesClientConfigMapPropertySourceLocator.class,
				configMapPropertySourceLocator.getClass());

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertSame(KubernetesClientSecretsPropertySourceLocator.class,
				secretsPropertySourceLocator.getClass());

	}

	/**
	 * both ConfigMapConfigProperties and SecretsConfigProperties are enabled explicitly,
	 * as such they are both registered.
	 *
	 * It also means that ConfigMapPropertySourceLocator and SecretsPropertySourceLocator
	 * are registered too.
	 *
	 * Since retry is not enabled explicitly, we also assert the types to ensure that
	 * these are not retryable beans.
	 */
	@Test
	void testBothPresentExplicitly() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.config.enabled", "true");
		environment.setProperty("spring.cloud.kubernetes.secrets.enabled", "true");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertTrue(context.isRegistered(KubernetesClientProperties.class));
		Assertions.assertTrue(context.isRegistered(CoreV1Api.class));
		Assertions.assertTrue(context.isRegistered(ApiClient.class));

		Assertions.assertTrue(context.isRegistered(ConfigMapConfigProperties.class));
		Assertions.assertTrue(context.isRegistered(SecretsConfigProperties.class));

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
				.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertSame(KubernetesClientConfigMapPropertySourceLocator.class,
				configMapPropertySourceLocator.getClass());

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertSame(KubernetesClientSecretsPropertySourceLocator.class,
				secretsPropertySourceLocator.getClass());
	}

	/*
	 * both ConfigMapConfigProperties and SecretsConfigProperties are enabled
	 * (via @Default on 'spring.cloud.kubernetes.config.enabled' and
	 * 'spring.cloud.kubernetes.secrets.enabled'); as such they are both registered.
	 *
	 * It also means that ConfigMapPropertySourceLocator and SecretsPropertySourceLocator
	 * are registered too.
	 *
	 * Since retry is enabled explicitly, we also assert the types to ensure that these
	 * are retryable beans.
	 */
	@Test
	void testBothPresentAndRetryEnabled() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.config.retry.enabled", "true");
		environment.setProperty("spring.cloud.kubernetes.config.fail-fast", "true");
		environment.setProperty("spring.cloud.kubernetes.secrets.retry.enabled", "true");
		environment.setProperty("spring.cloud.kubernetes.secrets.fail-fast", "true");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertTrue(context.isRegistered(KubernetesClientProperties.class));
		Assertions.assertTrue(context.isRegistered(CoreV1Api.class));
		Assertions.assertTrue(context.isRegistered(ApiClient.class));

		Assertions.assertTrue(context.isRegistered(ConfigMapConfigProperties.class));
		Assertions.assertTrue(context.isRegistered(SecretsConfigProperties.class));

		Assertions.assertTrue(context.isRegistered(ConfigMapPropertySourceLocator.class));
		Assertions.assertTrue(context.isRegistered(SecretsPropertySourceLocator.class));

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
				.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertSame(ConfigDataRetryableConfigMapPropertySourceLocator.class,
				configMapPropertySourceLocator.getClass());

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertSame(ConfigDataRetryableSecretsPropertySourceLocator.class,
				secretsPropertySourceLocator.getClass());

	}

}
