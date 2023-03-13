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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.springframework.boot.cloud.CloudPlatform.KUBERNETES;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;
import static org.springframework.util.ClassUtils.isPresent;

/**
 * @author Ryan Baxter
 */
public abstract class KubernetesConfigDataLocationResolver
		implements ConfigDataLocationResolver<KubernetesConfigDataResource>, Ordered {

	private static final boolean RETRY_IS_PRESENT = isPresent("org.springframework.retry.annotation.Retryable", null);

	private final Log log;

	public KubernetesConfigDataLocationResolver(DeferredLogFactory factory) {
		this.log = factory.getLog(KubernetesConfigDataLocationResolver.class);
	}

	protected final String getPrefix() {
		return "kubernetes:";
	}

	@Override
	public final int getOrder() {
		return -1;
	}

	@Override
	public final boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		return location.hasPrefix(getPrefix())
				&& (KUBERNETES.isEnforced(context.getBinder()) || KUBERNETES.isDetected(new StandardEnvironment()));
	}

	@Override
	public final List<KubernetesConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		return Collections.emptyList();
	}

	@Override
	public final List<KubernetesConfigDataResource> resolveProfileSpecific(
			ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location, Profiles profiles)
			throws ConfigDataLocationNotFoundException {
		PropertyHolder propertyHolder = PropertyHolder.of(resolverContext);
		KubernetesClientProperties clientProperties = propertyHolder.kubernetesClientProperties();
		ConfigMapConfigProperties configMapProperties = propertyHolder.configMapConfigProperties();
		SecretsConfigProperties secretsProperties = propertyHolder.secretsProperties();

		registerProperties(resolverContext, clientProperties, configMapProperties, secretsProperties);

		HashMap<String, Object> kubernetesConfigData = new HashMap<>();
		kubernetesConfigData.put("spring.cloud.kubernetes.client.namespace", clientProperties.namespace());
		if (propertyHolder.applicationName() != null) {
			// If its null it means sprig.application.name was not set so don't add it to
			// the property source
			kubernetesConfigData.put("spring.application.name", propertyHolder.applicationName());
		}
		PropertySource<Map<String, Object>> propertySource = new MapPropertySource("kubernetesConfigData",
				kubernetesConfigData);
		ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(propertySource);
		environment.setActiveProfiles(profiles.getAccepted().toArray(new String[0]));
		KubernetesNamespaceProvider namespaceProvider = kubernetesNamespaceProvider(environment);

		registerBeans(resolverContext, location, profiles, propertyHolder, namespaceProvider);

		KubernetesConfigDataResource resource = new KubernetesConfigDataResource(clientProperties, configMapProperties,
				secretsProperties, location.isOptional(), profiles, environment);
		resource.setLog(log);

		return List.of(resource);
	}

	protected abstract void registerBeans(ConfigDataLocationResolverContext resolverContext,
			ConfigDataLocation location, Profiles profiles, PropertyHolder propertyHolder,
			KubernetesNamespaceProvider namespaceProvider);

	protected final boolean isRetryEnabledForConfigMap(ConfigMapConfigProperties configMapProperties) {
		return RETRY_IS_PRESENT && configMapProperties != null && configMapProperties.retry().enabled()
				&& configMapProperties.failFast();
	}

	protected final boolean isRetryEnabledForSecrets(SecretsConfigProperties secretsProperties) {
		return RETRY_IS_PRESENT && secretsProperties != null && secretsProperties.retry().enabled()
				&& secretsProperties.failFast();
	}

	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	private void registerProperties(ConfigDataLocationResolverContext resolverContext,
			KubernetesClientProperties clientProperties, ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties) {

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		registerSingle(bootstrapContext, KubernetesClientProperties.class, clientProperties,
				"configDataKubernetesClientProperties");

		if (configMapProperties != null) {
			registerSingle(bootstrapContext, ConfigMapConfigProperties.class, configMapProperties,
					"configDataConfigMapConfigProperties");
		}

		if (secretsProperties != null) {
			registerSingle(bootstrapContext, SecretsConfigProperties.class, secretsProperties,
					"configDataSecretsConfigProperties");
		}
	}

	protected record PropertyHolder(KubernetesClientProperties kubernetesClientProperties,
			ConfigMapConfigProperties configMapConfigProperties, SecretsConfigProperties secretsProperties,
			String applicationName) {

		private static PropertyHolder of(ConfigDataLocationResolverContext context) {
			Binder binder = context.getBinder();

			String applicationName = binder.bind("spring.application.name", String.class).orElse(null);
			String namespace = binder.bind("spring.cloud.kubernetes.client.namespace", String.class)
					.orElse(binder.bind("kubernetes.namespace", String.class).orElse(""));

			KubernetesClientProperties kubernetesClientProperties = clientProperties(context, namespace);
			ConfigMapAndSecrets both = ConfigMapAndSecrets.of(binder);

			return new PropertyHolder(kubernetesClientProperties, both.configMapProperties(),
					both.secretsConfigProperties(), applicationName);
		}

		private static KubernetesClientProperties clientProperties(ConfigDataLocationResolverContext context,
				String namespace) {
			KubernetesClientProperties kubernetesClientProperties;

			if (context.getBootstrapContext().isRegistered(KubernetesClientProperties.class)) {
				kubernetesClientProperties = context.getBootstrapContext().get(KubernetesClientProperties.class)
						.withNamespace(namespace);
			}
			else {
				kubernetesClientProperties = context.getBinder()
						.bindOrCreate(KubernetesClientProperties.PREFIX, Bindable.of(KubernetesClientProperties.class))
						.withNamespace(namespace);
			}

			return kubernetesClientProperties;

		}

	}

	/**
	 * holds ConfigMapConfigProperties and SecretsConfigProperties, both can be null if
	 * using such sources is disabled.
	 */
	private record ConfigMapAndSecrets(ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsConfigProperties) {

		private static ConfigMapAndSecrets of(Binder binder) {

			boolean configEnabled = binder.bind("spring.cloud.kubernetes.config.enabled", boolean.class).orElse(true);
			boolean secretsEnabled = binder.bind("spring.cloud.kubernetes.secrets.enabled", boolean.class).orElse(true);

			ConfigMapConfigProperties configMapConfigProperties = null;
			if (configEnabled) {
				configMapConfigProperties = binder.bindOrCreate(ConfigMapConfigProperties.PREFIX,
						ConfigMapConfigProperties.class);
			}

			SecretsConfigProperties secretsProperties = null;
			if (secretsEnabled) {
				secretsProperties = binder.bindOrCreate(SecretsConfigProperties.PREFIX, SecretsConfigProperties.class);
			}

			return new ConfigMapAndSecrets(configMapConfigProperties, secretsProperties);

		}
	}

}
