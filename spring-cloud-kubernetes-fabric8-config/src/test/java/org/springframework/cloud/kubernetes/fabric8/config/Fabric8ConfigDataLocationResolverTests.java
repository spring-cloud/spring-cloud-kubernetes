/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
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
class Fabric8ConfigDataLocationResolverTests {

	private static final ConfigDataLocationResolverContext RESOLVER_CONTEXT = Mockito
		.mock(ConfigDataLocationResolverContext.class);

	private static final Fabric8ConfigDataLocationResolver RESOLVER = new Fabric8ConfigDataLocationResolver();

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

		Assertions.assertThat(context.isRegistered(KubernetesClientProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(Config.class)).isTrue();
		Assertions.assertThat(context.isRegistered(KubernetesClient.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapConfigProperties.class)).isFalse();
		Assertions.assertThat(context.isRegistered(SecretsConfigProperties.class)).isFalse();

		Assertions.assertThat(context.isRegistered(ConfigMapPropertySourceLocator.class)).isFalse();
		Assertions.assertThat(context.isRegistered(SecretsPropertySourceLocator.class)).isFalse();
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
		environment.setProperty("spring.cloud.kubernetes.secrets.enabled", "true");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertThat(context.isRegistered(KubernetesClientProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(Config.class)).isTrue();
		Assertions.assertThat(context.isRegistered(KubernetesClient.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapConfigProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(SecretsConfigProperties.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapPropertySourceLocator.class)).isTrue();
		Assertions.assertThat(context.isRegistered(SecretsPropertySourceLocator.class)).isTrue();

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
			.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertThat(configMapPropertySourceLocator.getClass())
			.isSameAs(Fabric8ConfigMapPropertySourceLocator.class);

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertThat(secretsPropertySourceLocator.getClass())
			.isSameAs(Fabric8SecretsPropertySourceLocator.class);

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

		Assertions.assertThat(context.isRegistered(KubernetesClientProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(Config.class)).isTrue();
		Assertions.assertThat(context.isRegistered(KubernetesClient.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapConfigProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(SecretsConfigProperties.class)).isTrue();

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
			.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertThat(configMapPropertySourceLocator.getClass())
			.isEqualTo(Fabric8ConfigMapPropertySourceLocator.class);

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertThat(secretsPropertySourceLocator.getClass())
			.isEqualTo(Fabric8SecretsPropertySourceLocator.class);
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
		environment.setProperty("spring.cloud.kubernetes.secrets.enabled", "true");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		Assertions.assertThat(context.isRegistered(KubernetesClientProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(Config.class)).isTrue();
		Assertions.assertThat(context.isRegistered(KubernetesClient.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapConfigProperties.class)).isTrue();
		Assertions.assertThat(context.isRegistered(SecretsConfigProperties.class)).isTrue();

		Assertions.assertThat(context.isRegistered(ConfigMapPropertySourceLocator.class)).isTrue();
		Assertions.assertThat(context.isRegistered(SecretsPropertySourceLocator.class)).isTrue();

		ConfigMapPropertySourceLocator configMapPropertySourceLocator = context
			.get(ConfigMapPropertySourceLocator.class);
		Assertions.assertThat(configMapPropertySourceLocator.getClass())
			.isEqualTo(ConfigDataRetryableConfigMapPropertySourceLocator.class);

		SecretsPropertySourceLocator secretsPropertySourceLocator = context.get(SecretsPropertySourceLocator.class);
		Assertions.assertThat(secretsPropertySourceLocator.getClass())
			.isEqualTo(ConfigDataRetryableSecretsPropertySourceLocator.class);
	}

}
