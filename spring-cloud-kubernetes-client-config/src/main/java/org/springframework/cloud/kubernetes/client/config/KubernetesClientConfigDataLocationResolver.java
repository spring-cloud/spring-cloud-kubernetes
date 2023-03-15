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

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.BootstrapRegistry;
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
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.kubernetesApiClient;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientConfigDataLocationResolver extends KubernetesConfigDataLocationResolver {

	public KubernetesClientConfigDataLocationResolver(DeferredLogFactory factory) {
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
		CoreV1Api coreV1Api = registerClientAndCoreV1Api(bootstrapContext, kubernetesClientProperties);

		if (configMapProperties != null && configMapProperties.enabled()) {
			ConfigMapPropertySourceLocator configMapPropertySourceLocator = new KubernetesClientConfigMapPropertySourceLocator(
					coreV1Api, configMapProperties, namespaceProvider);
			if (isRetryEnabledForConfigMap(configMapProperties)) {
				configMapPropertySourceLocator = new ConfigDataRetryableConfigMapPropertySourceLocator(
						configMapPropertySourceLocator, configMapProperties, new KubernetesClientConfigMapsCache());
			}

			registerSingle(bootstrapContext, ConfigMapPropertySourceLocator.class, configMapPropertySourceLocator,
					"configDataConfigMapPropertySourceLocator");
		}

		if (secretsProperties != null && secretsProperties.enabled()) {
			SecretsPropertySourceLocator secretsPropertySourceLocator = new KubernetesClientSecretsPropertySourceLocator(
					coreV1Api, namespaceProvider, secretsProperties);
			if (isRetryEnabledForSecrets(secretsProperties)) {
				secretsPropertySourceLocator = new ConfigDataRetryableSecretsPropertySourceLocator(
						secretsPropertySourceLocator, secretsProperties, new KubernetesClientSecretsCache());
			}

			registerSingle(bootstrapContext, SecretsPropertySourceLocator.class, secretsPropertySourceLocator,
					"configDataSecretsPropertySourceLocator");
		}
	}

	private CoreV1Api registerClientAndCoreV1Api(ConfigurableBootstrapContext bootstrapContext,
			KubernetesClientProperties kubernetesClientProperties) {
		ApiClient apiClient = kubernetesApiClient();
		apiClient.setUserAgent(kubernetesClientProperties.userAgent());
		registerSingle(bootstrapContext, ApiClient.class, apiClient, "configDataApiClient");

		CoreV1Api coreV1Api = new CoreV1Api(apiClient);
		bootstrapContext.registerIfAbsent(CoreV1Api.class, BootstrapRegistry.InstanceSupplier.of(coreV1Api));

		return coreV1Api;
	}

	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

}
