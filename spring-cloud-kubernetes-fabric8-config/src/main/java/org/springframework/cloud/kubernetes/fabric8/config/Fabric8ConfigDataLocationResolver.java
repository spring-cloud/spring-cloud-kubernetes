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
import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
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

/**
 * @author Ryan Baxter
 */
public class Fabric8ConfigDataLocationResolver extends KubernetesConfigDataLocationResolver {

	public Fabric8ConfigDataLocationResolver(Log log) {
		super(log);
	}

	@Override
	protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
			Profiles profiles, KubernetesConfigDataLocationResolver.PropertyHolder propertyHolder,
			KubernetesNamespaceProvider namespaceProvider) {
		KubernetesClientProperties properties = propertyHolder.kubernetesClientProperties();
		ConfigMapConfigProperties configMapProperties = propertyHolder.configMapConfigProperties();
		SecretsConfigProperties secretsProperties = propertyHolder.secretsProperties();

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		Config config = config(properties);
		bootstrapContext.registerIfAbsent(Config.class, InstanceSupplier.of(config));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
				.registerSingleton("fabric8Config", event.getBootstrapContext().get(Config.class)));

		KubernetesClient kubernetesClient = kubernetesClient(config);
		bootstrapContext.registerIfAbsent(KubernetesClient.class,
				BootstrapRegistry.InstanceSupplier.of(kubernetesClient));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
				.registerSingleton("configKubernetesClient", event.getBootstrapContext().get(KubernetesClient.class)));

		if (isRetryEnabled(configMapProperties, secretsProperties)) {
			registerRetryBeans(configMapProperties, secretsProperties, bootstrapContext, kubernetesClient,
					namespaceProvider);
		}
		else {
			if (configMapProperties.isEnabled()) {
				Fabric8ConfigMapPropertySourceLocator configMapPropertySourceLocator = new Fabric8ConfigMapPropertySourceLocator(
						kubernetesClient, configMapProperties, namespaceProvider);
				bootstrapContext.registerIfAbsent(ConfigMapPropertySourceLocator.class,
						InstanceSupplier.of(configMapPropertySourceLocator));
				bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
						.registerSingleton("configDataConfigMapPropertySourceLocator",
								event.getBootstrapContext().get(ConfigMapPropertySourceLocator.class)));
			}
			if (secretsProperties.isEnabled()) {
				Fabric8SecretsPropertySourceLocator secretsPropertySourceLocator = new Fabric8SecretsPropertySourceLocator(
						kubernetesClient, secretsProperties, namespaceProvider);
				bootstrapContext.registerIfAbsent(SecretsPropertySourceLocator.class,
						InstanceSupplier.of(secretsPropertySourceLocator));
				bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
						.registerSingleton("configDataSecretsPropertySourceLocator",
								event.getBootstrapContext().get(SecretsPropertySourceLocator.class)));
			}
		}
	}

	private void registerRetryBeans(ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties, ConfigurableBootstrapContext bootstrapContext,
			KubernetesClient kubernetesClient, KubernetesNamespaceProvider namespaceProvider) {
		if (configMapProperties.isEnabled()) {
			ConfigMapPropertySourceLocator configMapPropertySourceLocator = new Fabric8ConfigMapPropertySourceLocator(
					kubernetesClient, configMapProperties, namespaceProvider);
			if (isRetryEnabledForConfigMap(configMapProperties)) {
				configMapPropertySourceLocator = new ConfigDataRetryableConfigMapPropertySourceLocator(
						configMapPropertySourceLocator, configMapProperties);
			}

			bootstrapContext.registerIfAbsent(ConfigMapPropertySourceLocator.class,
					InstanceSupplier.of(configMapPropertySourceLocator));
			bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
					"configDataConfigMapPropertySourceLocator",
					event.getBootstrapContext().get(ConfigMapPropertySourceLocator.class)));
		}

		if (secretsProperties.isEnabled()) {
			SecretsPropertySourceLocator secretsPropertySourceLocator = new Fabric8SecretsPropertySourceLocator(
					kubernetesClient, secretsProperties, namespaceProvider);
			if (isRetryEnabledForSecrets(secretsProperties)) {
				secretsPropertySourceLocator = new ConfigDataRetryableSecretsPropertySourceLocator(
						secretsPropertySourceLocator, secretsProperties);
			}
			bootstrapContext.registerIfAbsent(SecretsPropertySourceLocator.class,
					InstanceSupplier.of(secretsPropertySourceLocator));
			bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
					"configDataSecretsPropertySourceLocator",
					event.getBootstrapContext().get(SecretsPropertySourceLocator.class)));
		}
	}

	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	private Config config(KubernetesClientProperties properties) {
		return new Fabric8AutoConfiguration().kubernetesClientConfig(properties);
	}

	private KubernetesClient kubernetesClient(Config config) {
		return new Fabric8AutoConfiguration().kubernetesClient(config);
	}

}
