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

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private final CoreV1Api coreV1Api;

	private final KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	private final SharedInformerFactory factory;

	private final String namespace;

	private final boolean monitorConfigMaps;

	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		// We need to pass an APIClient to the SharedInformerFactory because if we use the
		// default
		// constructor it will use the configured default APIClient but that may not
		// contain
		// an APIClient configured within the cluster and does not contain the necessary
		// certificate authorities for the cluster. This results in SSL errors.
		// See https://github.com/spring-cloud/spring-cloud-kubernetes/issues/885
		this.factory = new SharedInformerFactory(createApiClientForInformerClient());
		this.namespace = kubernetesNamespaceProvider.getNamespace();
		this.monitorConfigMaps = properties.isMonitoringConfigMaps();
	}

	@PostConstruct
	public void watch() {
		if (coreV1Api != null && monitorConfigMaps) {
			SharedIndexInformer<V1ConfigMap> configMapInformer = factory.sharedIndexInformerFor(
					(CallGeneratorParams params) -> coreV1Api.listNamespacedConfigMapCall(namespace, null, null, null,
							null, null, null, params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
					V1ConfigMap.class, V1ConfigMapList.class);
			configMapInformer.addEventHandler(new ResourceEventHandler<>() {
				@Override
				public void onAdd(V1ConfigMap obj) {
					log.info("ConfigMap " + obj.getMetadata().getName() + " was added.");
					onEvent(obj);
				}

				@Override
				public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
					log.info("ConfigMap " + newObj.getMetadata().getName() + " was added.");
					onEvent(newObj);
				}

				@Override
				public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
					log.info("ConfigMap " + obj.getMetadata() + " was deleted.");
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

	protected void onEvent(V1ConfigMap configMap) {
		log.debug("onEvent configMap: " + configMap.toString());
		boolean changed = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
				findPropertySources(KubernetesClientConfigMapPropertySource.class));
		if (changed) {
			log.info("Configuration change detected, reloading properties.");
			reloadProperties();
		}
		else {
			log.warn("Configuration change was not detected.");
		}

	}

}
