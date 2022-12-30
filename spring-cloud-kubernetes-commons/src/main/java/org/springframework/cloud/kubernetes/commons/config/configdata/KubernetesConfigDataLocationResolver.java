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

package org.springframework.cloud.kubernetes.commons.config.configdata;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.springframework.boot.cloud.CloudPlatform.KUBERNETES;
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

		ConfigDataProperties properties = ConfigDataProperties.of(resolverContext).register();

		Map<String, Object> kubernetesConfigData = new HashMap<>();
		kubernetesConfigData.put("spring.cloud.kubernetes.client.namespace", properties.clientProperties().namespace());
		String applicationName = resolverContext.getBinder().bind("spring.application.name", String.class).orElse(null);
		if (applicationName != null) {
			kubernetesConfigData.put("spring.application.name", applicationName);
		}

		Environment environment = environment(kubernetesConfigData, profiles);
		KubernetesNamespaceProvider namespaceProvider = kubernetesNamespaceProvider(environment);
		registerBeans(resolverContext, location, profiles, properties, namespaceProvider);

		KubernetesConfigDataResource resource = new KubernetesConfigDataResource(properties.clientProperties(),
				properties.configMapProperties(), properties.secretsProperties(), location.isOptional(), profiles,
				environment);

		return List.of(resource);
	}

	protected abstract void registerBeans(ConfigDataLocationResolverContext resolverContext,
			ConfigDataLocation location, Profiles profiles, ConfigDataProperties properties,
			KubernetesNamespaceProvider namespaceProvider);

	protected final boolean isRetryEnabledForConfigMap(ConfigMapConfigProperties configMapProperties) {
		return RETRY_IS_PRESENT && configMapProperties != null && configMapProperties.retry().enabled()
				&& configMapProperties.failFast();
	}

	protected final boolean isRetryEnabledForSecrets(SecretsConfigProperties secretsProperties) {
		return RETRY_IS_PRESENT && secretsProperties != null && secretsProperties.retry().enabled()
				&& secretsProperties.failFast();
	}

	private KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	private Environment environment(Map<String, Object> kubernetesConfigData, Profiles profiles) {
		PropertySource<Map<String, Object>> propertySource = new MapPropertySource("kubernetesConfigData",
			kubernetesConfigData);
		ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(propertySource);
		environment.setActiveProfiles(profiles.getAccepted().toArray(new String[0]));
		return environment;
	}

}
