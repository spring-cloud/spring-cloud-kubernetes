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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigDataRetryableConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.ConfigDataRetryableSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigDataLocationResolver;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;

/**
 * @author Ryan Baxter
 */
public class Fabric8ConfigDataLocationResolver extends KubernetesConfigDataLocationResolver {

	public Fabric8ConfigDataLocationResolver(DeferredLogFactory factory) {
		super(factory);
	}

	@Override
	protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
			Profiles profiles, KubernetesConfigDataLocationResolver.PropertyHolder propertyHolder,
			KubernetesNamespaceProvider namespaceProvider) {
		KubernetesClientProperties kubernetesClientProperties = propertyHolder.kubernetesClientProperties();
		ConfigMapConfigProperties configMapProperties = propertyHolder.configMapConfigProperties();
		SecretsConfigProperties secretsProperties = propertyHolder.secretsProperties();

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		KubernetesClient kubernetesClient = registerConfigAndClient(bootstrapContext, kubernetesClientProperties);

		if (configMapProperties != null && configMapProperties.enabled()) {
			ConfigMapPropertySourceLocator configMapPropertySourceLocator = new Fabric8ConfigMapPropertySourceLocator(
					kubernetesClient, configMapProperties, namespaceProvider);
			if (isRetryEnabledForConfigMap(configMapProperties)) {
				configMapPropertySourceLocator = new ConfigDataRetryableConfigMapPropertySourceLocator(
						configMapPropertySourceLocator, configMapProperties, new Fabric8ConfigMapsCache());
			}

			registerSingle(bootstrapContext, ConfigMapPropertySourceLocator.class, configMapPropertySourceLocator,
					"configDataConfigMapPropertySourceLocator");
		}

		if (secretsProperties != null && secretsProperties.enabled()) {
			SecretsPropertySourceLocator secretsPropertySourceLocator = new Fabric8SecretsPropertySourceLocator(
					kubernetesClient, secretsProperties, namespaceProvider);
			if (isRetryEnabledForSecrets(secretsProperties)) {
				secretsPropertySourceLocator = new ConfigDataRetryableSecretsPropertySourceLocator(
						secretsPropertySourceLocator, secretsProperties, new Fabric8SecretsCache());
			}

			registerSingle(bootstrapContext, SecretsPropertySourceLocator.class, secretsPropertySourceLocator,
					"configDataSecretsPropertySourceLocator");
		}

	}

	private KubernetesClient registerConfigAndClient(ConfigurableBootstrapContext bootstrapContext,
			KubernetesClientProperties kubernetesClientProperties) {
		Config config = new Fabric8AutoConfiguration().kubernetesClientConfig(kubernetesClientProperties);
		registerSingle(bootstrapContext, Config.class, config, "fabric8Config");

		KubernetesClient kubernetesClient = new Fabric8AutoConfiguration().kubernetesClient(config);
		registerSingle(bootstrapContext, KubernetesClient.class, kubernetesClient, "configKubernetesClient");
		return kubernetesClient;
	}

	@Override
	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

}
