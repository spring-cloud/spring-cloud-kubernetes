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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;

/**
 * @author Ryan Baxter
 */
public abstract class KubernetesConfigDataLocationResolver
		implements ConfigDataLocationResolver<KubernetesConfigDataResource>, Ordered {

	static final boolean RETRY_IS_PRESENT = ClassUtils.isPresent("org.springframework.retry.annotation.Retryable",
			null);

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "kubernetes:";

	private final Log log;

	public KubernetesConfigDataLocationResolver(Log log) {
		this.log = log;
	}

	protected String getPrefix() {
		return PREFIX;
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		if (!location.hasPrefix(getPrefix())) {
			return false;
		}
		return (CloudPlatform.KUBERNETES.isEnforced(context.getBinder())
				|| CloudPlatform.KUBERNETES.isDetected(new StandardEnvironment()));
	}

	@Override
	public List<KubernetesConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		return Collections.emptyList();
	}

	@Override
	public List<KubernetesConfigDataResource> resolveProfileSpecific(ConfigDataLocationResolverContext resolverContext,
			ConfigDataLocation location, Profiles profiles) throws ConfigDataLocationNotFoundException {
		PropertyHolder propertyHolder = loadProperties(resolverContext);
		KubernetesClientProperties properties = propertyHolder.kubernetesClientProperties;
		ConfigMapConfigProperties configMapProperties = propertyHolder.configMapConfigProperties;
		SecretsConfigProperties secretsProperties = propertyHolder.secretsProperties;

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		bootstrapContext.registerIfAbsent(KubernetesClientProperties.class, InstanceSupplier.of(properties));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
				"configDataKubernetesClientProperties",
				event.getBootstrapContext().get(KubernetesClientProperties.class)));

		bootstrapContext.registerIfAbsent(ConfigMapConfigProperties.class, InstanceSupplier.of(configMapProperties));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
				"configDataConfigMapConfigProperties",
				event.getBootstrapContext().get(ConfigMapConfigProperties.class)));

		bootstrapContext.registerIfAbsent(SecretsConfigProperties.class, InstanceSupplier.of(secretsProperties));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
				"configDataSecretsConfigProperties", event.getBootstrapContext().get(SecretsConfigProperties.class)));

		HashMap<String, Object> kubernetesConfigData = new HashMap<>();
		kubernetesConfigData.put("spring.cloud.kubernetes.client.namespace", properties.getNamespace());
		if (propertyHolder.applicationName != null) {
			// If its null it means sprig.application.name was not set so don't add it to
			// the property source
			kubernetesConfigData.put("spring.application.name", propertyHolder.applicationName);
		}
		PropertySource<Map<String, Object>> propertySource = new MapPropertySource("kubernetesConfigData",
				kubernetesConfigData);
		ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(propertySource);
		environment.setActiveProfiles(profiles.getAccepted().toArray(new String[0]));
		KubernetesNamespaceProvider namespaceProvider = kubernetesNamespaceProvider(environment);

		registerBeans(resolverContext, location, profiles, propertyHolder, namespaceProvider);

		KubernetesConfigDataResource resource = new KubernetesConfigDataResource(properties, configMapProperties,
				secretsProperties, location.isOptional(), profiles, environment);
		resource.setLog(log);

		List<KubernetesConfigDataResource> locations = new ArrayList<>();
		locations.add(resource);

		return locations;
	}

	protected abstract void registerBeans(ConfigDataLocationResolverContext resolverContext,
			ConfigDataLocation location, Profiles profiles, PropertyHolder propertyHolder,
			KubernetesNamespaceProvider namespaceProvider);

	protected boolean isRetryEnabled(ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties) {
		return isRetryEnabledForConfigMap(configMapProperties) || isRetryEnabledForSecrets(secretsProperties);
	}

	protected boolean isRetryEnabledForConfigMap(ConfigMapConfigProperties configMapProperties) {
		return RETRY_IS_PRESENT && configMapProperties.getRetry().isEnabled() && configMapProperties.isFailFast();
	}

	protected boolean isRetryEnabledForSecrets(SecretsConfigProperties secretsProperties) {
		return RETRY_IS_PRESENT && secretsProperties.getRetry().isEnabled() && secretsProperties.isFailFast();
	}

	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	@Override
	public int getOrder() {
		return -1;
	}

	private BindHandler getBindHandler(ConfigDataLocationResolverContext context) {
		return context.getBootstrapContext().getOrElse(BindHandler.class, null);
	}

	protected PropertyHolder loadProperties(ConfigDataLocationResolverContext context) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);
		String applicationName = binder.bind("spring.application.name", String.class).orElse(null);
		String namespace = binder.bind("spring.cloud.kubernetes.client.namespace", String.class)
				.orElse(binder.bind("kubernetes.namespace", String.class).orElse(""));

		KubernetesClientProperties kubernetesClientProperties;
		if (context.getBootstrapContext().isRegistered(KubernetesClientProperties.class)) {
			kubernetesClientProperties = new KubernetesClientProperties();
			BeanUtils.copyProperties(context.getBootstrapContext().get(KubernetesClientProperties.class),
					kubernetesClientProperties);
		}
		else {
			kubernetesClientProperties = binder
					.bind(KubernetesClientProperties.PREFIX, Bindable.of(KubernetesClientProperties.class), bindHandler)
					.orElseGet(KubernetesClientProperties::new);
		}
		kubernetesClientProperties.setNamespace(namespace);

		ConfigMapConfigProperties configMapConfigProperties = binder
				.bind(ConfigMapConfigProperties.PREFIX, ConfigMapConfigProperties.class)
				.orElseGet(ConfigMapConfigProperties::new);
		SecretsConfigProperties secretsProperties = binder
				.bind(SecretsConfigProperties.PREFIX, SecretsConfigProperties.class)
				.orElseGet(SecretsConfigProperties::new);
		return new PropertyHolder(kubernetesClientProperties, configMapConfigProperties,
				secretsProperties, applicationName);
	}

	protected record PropertyHolder(KubernetesClientProperties kubernetesClientProperties,
			ConfigMapConfigProperties configMapConfigProperties, SecretsConfigProperties secretsProperties,
			String applicationName) {
	}

}
