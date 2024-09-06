/*
 * Copyright 2013-2023 the original author or authors.
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

import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver;
import org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver.PropertyResolver;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.util.ClassUtils;

/**
 * @author Ryan Baxter
 */
public abstract class KubernetesConfigServerBootstrapper implements BootstrapRegistryInitializer {

	public static boolean hasConfigServerInstanceProvider() {
		return !ClassUtils.isPresent("org.springframework.cloud.config.client.ConfigServerInstanceProvider", null);
	}

	public static KubernetesDiscoveryProperties createKubernetesDiscoveryProperties(Binder binder,
			BindHandler bindHandler) {
		return binder
			.bind(KubernetesDiscoveryProperties.PREFIX, Bindable.of(KubernetesDiscoveryProperties.class), bindHandler)
			.orElseGet(() -> KubernetesDiscoveryProperties.DEFAULT);
	}

	public static KubernetesDiscoveryProperties createKubernetesDiscoveryProperties(BootstrapContext bootstrapContext) {
		PropertyResolver propertyResolver = getPropertyResolver(bootstrapContext);
		return propertyResolver.resolveConfigurationProperties(KubernetesDiscoveryProperties.PREFIX,
				KubernetesDiscoveryProperties.class, () -> KubernetesDiscoveryProperties.DEFAULT);
	}

	public static KubernetesClientProperties createKubernetesClientProperties(Binder binder, BindHandler bindHandler) {
		return binder.bindOrCreate(KubernetesClientProperties.PREFIX, Bindable.of(KubernetesClientProperties.class))
			.withNamespace(new KubernetesNamespaceProvider(binder, bindHandler).getNamespace());
	}

	public static KubernetesClientProperties createKubernetesClientProperties(BootstrapContext bootstrapContext) {
		PropertyResolver propertyResolver = getPropertyResolver(bootstrapContext);
		return getPropertyResolver(bootstrapContext)
			.resolveOrCreateConfigurationProperties(KubernetesClientProperties.PREFIX, KubernetesClientProperties.class)
			.withNamespace(propertyResolver.get(KubernetesNamespaceProvider.NAMESPACE_PROPERTY, String.class, null));
	}

	public static Boolean getDiscoveryEnabled(Binder binder, BindHandler bindHandler) {
		return binder.bind(ConfigClientProperties.CONFIG_DISCOVERY_ENABLED, Bindable.of(Boolean.class), bindHandler)
			.orElse(false);
	}

	public static Boolean getDiscoveryEnabled(BootstrapContext bootstrapContext) {
		return getPropertyResolver(bootstrapContext).get(ConfigClientProperties.CONFIG_DISCOVERY_ENABLED, Boolean.class,
				false);
	}

	protected static PropertyResolver getPropertyResolver(BootstrapContext context) {
		return context.getOrElseSupply(ConfigServerConfigDataLocationResolver.PropertyResolver.class,
				() -> new ConfigServerConfigDataLocationResolver.PropertyResolver(context.get(Binder.class),
						context.getOrElse(BindHandler.class, null)));
	}

}
