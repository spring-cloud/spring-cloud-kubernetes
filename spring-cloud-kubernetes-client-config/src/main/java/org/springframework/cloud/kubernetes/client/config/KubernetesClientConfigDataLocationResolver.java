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
import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.cloud.kubernetes.client.KubernetesClientPodUtils;
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

/**
 * @author Ryan Baxter
 */
public class KubernetesClientConfigDataLocationResolver extends KubernetesConfigDataLocationResolver {

	public KubernetesClientConfigDataLocationResolver(Log log) {
		super(log);
	}

	@Override
	protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
			Profiles profiles, KubernetesConfigDataLocationResolver.PropertyHolder propertyHolder,
			KubernetesNamespaceProvider namespaceProvider) {
		ConfigMapConfigProperties configMapProperties = propertyHolder.configMapConfigProperties();
		SecretsConfigProperties secretsProperties = propertyHolder.secretsProperties();

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		ApiClient apiClient = kubernetesApiClient();
		bootstrapContext.registerIfAbsent(ApiClient.class, InstanceSupplier.of(apiClient));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
				.registerSingleton("configDataApiClient", event.getBootstrapContext().get(ApiClient.class)));

		CoreV1Api coreV1Api = coreApi(apiClient);
		bootstrapContext.registerIfAbsent(CoreV1Api.class, InstanceSupplier.of(coreV1Api));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
				.registerSingleton("configCoreV1Api", event.getBootstrapContext().get(CoreV1Api.class)));

		if (isRetryEnabled(configMapProperties, secretsProperties)) {
			registerRetryBeans(configMapProperties, secretsProperties, bootstrapContext, coreV1Api, namespaceProvider);
		}
		else {
			if (configMapProperties.isEnabled()) {
				KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator = new KubernetesClientConfigMapPropertySourceLocator(
						coreV1Api, configMapProperties, namespaceProvider);
				bootstrapContext.registerIfAbsent(ConfigMapPropertySourceLocator.class,
						InstanceSupplier.of(configMapPropertySourceLocator));
				bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory()
						.registerSingleton("configDataConfigMapPropertySourceLocator",
								event.getBootstrapContext().get(ConfigMapPropertySourceLocator.class)));
			}

			if (secretsProperties.isEnabled()) {
				KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator = new KubernetesClientSecretsPropertySourceLocator(
						coreV1Api, namespaceProvider, secretsProperties);
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
			CoreV1Api coreV1Api, KubernetesNamespaceProvider namespaceProvider) {
		if (configMapProperties.isEnabled()) {
			ConfigMapPropertySourceLocator configMapPropertySourceLocator = new KubernetesClientConfigMapPropertySourceLocator(
					coreV1Api, configMapProperties, namespaceProvider);
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
			SecretsPropertySourceLocator secretsPropertySourceLocator = new KubernetesClientSecretsPropertySourceLocator(
					coreV1Api, namespaceProvider, secretsProperties);
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

	protected ApiClient apiClient(KubernetesClientProperties properties) {
		ApiClient apiClient = kubernetesApiClient();
		apiClient.setUserAgent(properties.getUserAgent());
		return apiClient;
	}

	protected CoreV1Api coreApi(ApiClient apiClient) {
		return new CoreV1Api(apiClient);
	}

	protected KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	protected KubernetesClientPodUtils kubernetesPodUtils(CoreV1Api client,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new KubernetesClientPodUtils(client, kubernetesNamespaceProvider.getNamespace());
	}

}
