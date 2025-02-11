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

package org.springframework.cloud.kubernetes.configserver;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigContext;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesConfigEnabled;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesSecretsEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.configserver.KubernetesPropertySourceSupplier.namespaceSplitter;

/**
 * @author Ryan Baxter
 */
@Configuration
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class })
@AutoConfigureBefore({ ConfigServerAutoConfiguration.class })
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@EnableConfigurationProperties(KubernetesConfigServerProperties.class)
public class KubernetesConfigServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public KubernetesEnvironmentRepositoryFactory kubernetesEnvironmentRepositoryFactory(
			ObjectProvider<KubernetesEnvironmentRepository> kubernetesEnvironmentRepositoryProvider) {
		return new KubernetesEnvironmentRepositoryFactory(kubernetesEnvironmentRepositoryProvider);
	}

	@Bean
	@Profile("kubernetes")
	@ConditionalOnMissingBean
	public EnvironmentRepository kubernetesEnvironmentRepository(CoreV1Api coreV1Api,
			List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new KubernetesEnvironmentRepository(coreV1Api, kubernetesPropertySourceSuppliers,
				kubernetesNamespaceProvider.getNamespace());
	}

	@Bean
	@ConditionalOnKubernetesConfigEnabled
	@ConditionalOnProperty(value = "spring.cloud.kubernetes.config.enableApi", matchIfMissing = true)
	public KubernetesPropertySourceSupplier configMapPropertySourceSupplier(
			KubernetesConfigServerProperties properties) {
		return (coreApi, applicationName, namespace, springEnv) -> {
			List<String> namespaces = namespaceSplitter(properties.getConfigMapNamespaces(), namespace);
			List<MapPropertySource> propertySources = new ArrayList<>();

			namespaces.forEach(space -> {

				NamedConfigMapNormalizedSource source = new NamedConfigMapNormalizedSource(applicationName, space,
						false, ConfigUtils.Prefix.DEFAULT, true, true);
				KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreApi, source, space,
						springEnv, false);

				propertySources.add(new KubernetesClientConfigMapPropertySource(context));
			});
			return propertySources;
		};
	}

	@Bean
	@ConditionalOnKubernetesSecretsEnabled
	@ConditionalOnProperty("spring.cloud.kubernetes.secrets.enableApi")
	public KubernetesPropertySourceSupplier secretsPropertySourceSupplier(KubernetesConfigServerProperties properties) {
		return (coreApi, applicationName, namespace, springEnv) -> {
			List<String> namespaces = namespaceSplitter(properties.getSecretsNamespaces(), namespace);
			List<MapPropertySource> propertySources = new ArrayList<>();

			namespaces.forEach(space -> {
				NormalizedSource source = new NamedSecretNormalizedSource(applicationName, space, false,
						ConfigUtils.Prefix.DEFAULT, true, true);
				KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreApi, source, space,
						springEnv, false);
				propertySources.add(new KubernetesClientSecretsPropertySource(context));
			});

			return propertySources;
		};
	}

}
