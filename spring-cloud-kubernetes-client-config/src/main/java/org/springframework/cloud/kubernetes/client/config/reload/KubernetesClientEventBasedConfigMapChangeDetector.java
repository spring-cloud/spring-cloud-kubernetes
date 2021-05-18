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
import javax.annotation.PreDestroy;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import okhttp3.OkHttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
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
public class KubernetesClientEventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private static final Log LOG = LogFactory.getLog(KubernetesClientEventBasedConfigMapChangeDetector.class);

	private CoreV1Api coreV1Api = null;

	private KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	private SharedInformerFactory factory;

	private KubernetesClientProperties kubernetesClientProperties;

	private KubernetesNamespaceProvider kubernetesNamespaceProvider;

	@Deprecated
	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesClientProperties kubernetesClientProperties) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		this.factory = new SharedInformerFactory();
		this.kubernetesClientProperties = kubernetesClientProperties;
	}

	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		this.factory = new SharedInformerFactory();
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Deprecated
	public KubernetesClientEventBasedConfigMapChangeDetector(ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesClientProperties kubernetesClientProperties) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
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
		this.factory = new SharedInformerFactory();
	}

	private String getNamespace() {
		return kubernetesNamespaceProvider != null ? kubernetesNamespaceProvider.getNamespace()
				: kubernetesClientProperties.getNamespace();
	}

	@PostConstruct
	public void watch() {
		if (coreV1Api != null && this.properties.isMonitoringConfigMaps()) {
			SharedIndexInformer<V1ConfigMap> configMapInformer = factory.sharedIndexInformerFor(
					(CallGeneratorParams params) -> coreV1Api.listNamespacedConfigMapCall(getNamespace(), null, null,
							null, null, null, null, params.resourceVersion, null, params.timeoutSeconds, params.watch,
							null),
					V1ConfigMap.class, V1ConfigMapList.class);
			configMapInformer.addEventHandler(new ResourceEventHandler<V1ConfigMap>() {
				@Override
				public void onAdd(V1ConfigMap obj) {
					LOG.info("CongifMap " + obj.getMetadata().getName() + " was added.");
					onEvent(obj);
				}

				@Override
				public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
					LOG.info("ConfigMap " + newObj.getMetadata().getName() + " was added.");
					onEvent(newObj);
				}

				@Override
				public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
					LOG.info("ConfigMap " + obj.getMetadata() + " was deleted.");
					onEvent(obj);
				}
			});
			factory.startAllRegisteredInformers();
		}
	}

	@PreDestroy
	public void unwatch() {
		factory.stopAllRegisteredInformers();
	}

	private void onEvent(V1ConfigMap configMap) {
		this.log.debug(String.format("onEvent configMap: %s", configMap.toString()));
		boolean changed = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
				findPropertySources(KubernetesClientConfigMapPropertySource.class));
		if (changed) {
			LOG.info("Configuration change detected, reloading properties.");
			reloadProperties();
		}
		else {
			LOG.warn("Configuration change was not detected.");
		}

	}

}
