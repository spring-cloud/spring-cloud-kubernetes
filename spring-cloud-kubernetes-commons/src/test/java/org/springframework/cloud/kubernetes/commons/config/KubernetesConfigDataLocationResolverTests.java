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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class KubernetesConfigDataLocationResolverTests {

	private static final DeferredLogFactory FACTORY = Supplier::get;

	// implementation that does nothing when registerBeans is called
	private static final KubernetesConfigDataLocationResolver NOOP_RESOLVER = new KubernetesConfigDataLocationResolver(
			FACTORY) {
		@Override
		protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
				Profiles profiles, PropertyHolder propertyHolder, KubernetesNamespaceProvider namespaceProvider) {
		}
	};

	private static final ConfigDataLocationResolverContext RESOLVER_CONTEXT = Mockito
			.mock(ConfigDataLocationResolverContext.class);

	@Test
	void testGetPrefix() {
		Assertions.assertEquals("kubernetes:", NOOP_RESOLVER.getPrefix());
	}

	/**
	 * method returns true via 'KUBERNETES.isEnforced(context.getBinder())'
	 */
	@Test
	void testIsResolvableTrue() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.main.cloud-platform", "KUBERNETES");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);

		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		boolean result = NOOP_RESOLVER.isResolvable(RESOLVER_CONTEXT, configDataLocation);
		Assertions.assertTrue(result);
	}

	@Test
	void testIsResolvableFalse() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);

		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		boolean result = NOOP_RESOLVER.isResolvable(RESOLVER_CONTEXT, configDataLocation);
		Assertions.assertFalse(result);
	}

	@Test
	void testResolve() {
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolve(RESOLVER_CONTEXT, configDataLocation);
		Assertions.assertEquals(0, result.size());
	}

	/**
	 * <pre>
	 * a test that only looks at 3 properties:
	 *   - application name
	 *   - namespace (via 'spring.cloud.kubernetes.client.namespace')
	 *   - KubernetesClientProperties (created via bindOrCreate)
	 * </pre>
	 */
	@Test
	void testResolveProfileSpecificOne() {

		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.application.name", "k8s-app-name");
		environment.setProperty("spring.cloud.kubernetes.client.namespace", "non-default-namespace");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("k8s-app-name",
				result.get(0).getEnvironment().getRequiredProperty("spring.application.name"));
		Assertions.assertEquals("non-default-namespace",
				result.get(0).getEnvironment().getRequiredProperty("spring.cloud.kubernetes.client.namespace"));
		// ensures that we called 'bindOrCreate' and as such @Default is picked-up
		Assertions.assertEquals("Spring-Cloud-Kubernetes-Application", result.get(0).getProperties().userAgent());
		Assertions.assertEquals("non-default-namespace", result.get(0).getProperties().namespace());

	}

	/**
	 * <pre>
	 * a test that only looks at 3 properties:
	 *   - application name
	 *   - namespace (via 'kubernetes.namespace')
	 *   - KubernetesClientProperties (bind from bootstrap context)
	 * </pre>
	 */
	@Test
	void testResolveProfileSpecificTwo() {

		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.application.name", "k8s-app-name");
		environment.setProperty("kubernetes.namespace", "non-default-namespace");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		DefaultBootstrapContext context = new DefaultBootstrapContext();
		KubernetesClientProperties properties = new KubernetesClientProperties(null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, "user-agent");
		context.register(KubernetesClientProperties.class, BootstrapRegistry.InstanceSupplier.of(properties));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(context);

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("k8s-app-name",
				result.get(0).getEnvironment().getRequiredProperty("spring.application.name"));
		Assertions.assertEquals("non-default-namespace",
				result.get(0).getEnvironment().getRequiredProperty("spring.cloud.kubernetes.client.namespace"));
		// ensures we bind existing from bootstrap context, and not call 'bindOrCreate'
		Assertions.assertEquals("user-agent", result.get(0).getProperties().userAgent());
		Assertions.assertEquals("non-default-namespace", result.get(0).getProperties().namespace());
	}

	/**
	 * test that asserts that we registered 3 property classes via 'registerProperties'
	 */
	@Test
	void testResolveProfileSpecificThree() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		KubernetesClientProperties kubernetesClientProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.get(KubernetesClientProperties.class);
		ConfigMapConfigProperties configMapConfigProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.get(ConfigMapConfigProperties.class);
		SecretsConfigProperties secretsConfigProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.get(SecretsConfigProperties.class);

		Assertions.assertNotNull(kubernetesClientProperties);
		Assertions.assertNotNull(configMapConfigProperties);
		Assertions.assertNotNull(secretsConfigProperties);
	}

	/**
	 * test that asserts that we registered 1 property class via 'registerProperties' The
	 * other two are disabled, on purpose.
	 */
	@Test
	void testResolveProfileSpecificFour() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.config.enabled", "false");
		environment.setProperty("spring.cloud.kubernetes.secrets.enabled", "false");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT, configDataLocation, profiles);

		// 'one' and 'two' prove that we have not registered ConfigMapConfigProperties and
		// SecretsConfigProperties in the bootstrap context
		ConfigMapConfigProperties one = new ConfigMapConfigProperties(false, List.of(), List.of(), Map.of(), false,
				null, null, false, false, false, null);

		SecretsConfigProperties two = new SecretsConfigProperties(false, Map.of(), List.of(), List.of(), false, null,
				null, false, false, false, null);

		KubernetesClientProperties kubernetesClientProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.get(KubernetesClientProperties.class);
		ConfigMapConfigProperties configMapConfigProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.getOrElse(ConfigMapConfigProperties.class, one);
		SecretsConfigProperties secretsConfigProperties = RESOLVER_CONTEXT.getBootstrapContext()
				.getOrElse(SecretsConfigProperties.class, two);

		Assertions.assertNotNull(kubernetesClientProperties);
		Assertions.assertSame(one, configMapConfigProperties);
		Assertions.assertSame(two, secretsConfigProperties);
	}

	/**
	 * test that proves that ConfigMapConfigProperties and SecretsConfigProperties are
	 * created with @Default values
	 */
	@Test
	void testResolveProfileSpecificFive() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		// we have @DefaultValue("true") boolean enableApi
		Assertions.assertTrue(result.get(0).getConfigMapProperties().enableApi());

		// we have @DefaultValue("true") boolean enabled
		Assertions.assertTrue(result.get(0).getSecretsConfigProperties().enabled());
	}

	/**
	 * test that proves that ConfigMapConfigProperties and SecretsConfigProperties are
	 * bind with existing properties
	 */
	@Test
	void testResolveProfileSpecificSix() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.config.enable-api", "false");
		environment.setProperty("spring.cloud.kubernetes.secrets.paths[0]", "a");
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		// we have @DefaultValue("true") boolean enableApi, but it is not going to be
		// picked up
		// because of the explicit property we set in environment
		Assertions.assertFalse(result.get(0).getConfigMapProperties().enableApi());
		// on the other hand, @Default will be picked here
		Assertions.assertTrue(result.get(0).getConfigMapProperties().enabled());

		// we have @DefaultValue enabled on paths, but it is not going to be picked up
		// because of the explicit property we set in environment
		Assertions.assertEquals("a", result.get(0).getSecretsConfigProperties().paths().get(0));
		// on the other hand, @Default will be picked here
		Assertions.assertTrue(result.get(0).getSecretsConfigProperties().includeProfileSpecificSources());
	}

	@Test
	void testIsOptional() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		Assertions.assertFalse(result.get(0).isOptional());
	}

	@Test
	void testProfiles() {
		MockEnvironment environment = new MockEnvironment();
		ConfigurationPropertySources.attach(environment);
		Binder binder = new Binder(ConfigurationPropertySources.get(environment));

		Mockito.when(RESOLVER_CONTEXT.getBinder()).thenReturn(binder);
		Mockito.when(RESOLVER_CONTEXT.getBootstrapContext()).thenReturn(new DefaultBootstrapContext());

		Profiles profiles = Mockito.mock(Profiles.class);
		Mockito.when(profiles.getAccepted()).thenReturn(List.of("a", "b"));

		ConfigDataLocation configDataLocation = ConfigDataLocation.of("kubernetes:abc");
		List<KubernetesConfigDataResource> result = NOOP_RESOLVER.resolveProfileSpecific(RESOLVER_CONTEXT,
				configDataLocation, profiles);

		Assertions.assertEquals(List.of("a", "b"),
				Arrays.stream(result.get(0).getEnvironment().getActiveProfiles()).toList());
		Assertions.assertEquals("a,b", result.get(0).getProfiles());
	}

}
