/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import okhttp3.OkHttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.kubernetesApiClient;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private static final Log LOG = LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class);

	private CoreV1Api coreV1Api;

	private KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

	private SharedInformerFactory factory;

	private KubernetesClientProperties kubernetesClientProperties;

	private KubernetesNamespaceProvider kubernetesNamespaceProvider;

	@Deprecated
	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesClientProperties kubernetesClientProperties) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.factory = new SharedInformerFactory();
		this.coreV1Api = coreV1Api;
		this.kubernetesClientProperties = kubernetesClientProperties;
	}

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.factory = new SharedInformerFactory();
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Deprecated
	public KubernetesClientEventBasedSecretsChangeDetector(ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesClientProperties kubernetesClientProperties) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.factory = new SharedInformerFactory();
		this.kubernetesClientProperties = kubernetesClientProperties;
		try {
			ApiClient apiClient = kubernetesApiClient();
			OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
			apiClient.setHttpClient(httpClient);
			this.coreV1Api = new CoreV1Api(apiClient);
		}
		catch (Exception e) {
			LOG.error("Failed to create Kubernetes API client.  Event based ConfigMap monitoring will not work", e);
		}
	}

	private String getNamespace() {
		return kubernetesNamespaceProvider != null ? kubernetesNamespaceProvider.getNamespace()
				: kubernetesClientProperties.getNamespace();
	}

	@PostConstruct
	public void watch() {
		if (coreV1Api != null && this.properties.isMonitoringSecrets()) {
			SharedIndexInformer<V1Secret> configMapInformer = factory.sharedIndexInformerFor(
					(CallGeneratorParams params) -> coreV1Api.listNamespacedSecretCall(getNamespace(), null, null, null,
							null, null, null, params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
					V1Secret.class, V1SecretList.class);
			configMapInformer.addEventHandler(new ResourceEventHandler<V1Secret>() {
				@Override
				public void onAdd(V1Secret obj) {
					LOG.info("Secret " + obj.getMetadata().getName() + " was added.");
					onEvent(obj);
				}

				@Override
				public void onUpdate(V1Secret oldObj, V1Secret newObj) {
					LOG.info("Secret " + newObj.getMetadata().getName() + " was added.");
					onEvent(newObj);
				}

				@Override
				public void onDelete(V1Secret obj, boolean deletedFinalStateUnknown) {
					LOG.info("Secret " + obj.getMetadata() + " was deleted.");
					onEvent(obj);
				}
			});
			factory.startAllRegisteredInformers();
		}
	}

	private void onEvent(V1Secret secret) {
		this.log.debug(String.format("onEvent configMap: %s", secret.toString()));
		boolean changed = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
				findPropertySources(KubernetesClientSecretsPropertySource.class));
		if (changed) {
			this.log.info("Detected change in secrets");
			reloadProperties();
		}
	}

}
