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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private final CoreV1Api coreV1Api;

	private final KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	private final boolean monitorConfigMaps;

	private final SharedInformerFactory factory;

	private final List<SharedIndexInformer<V1ConfigMap>> informers = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final ResourceEventHandler<V1ConfigMap> handler = new ResourceEventHandler<>() {

		@Override
		public void onAdd(V1ConfigMap obj) {
			log.debug(() -> "ConfigMap " + obj.getMetadata().getName() + " was added.");
			onEvent(obj);
		}

		@Override
		public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
			log.debug(() -> "ConfigMap " + newObj.getMetadata().getName() + " was updated.");
			onEvent(newObj);
		}

		@Override
		public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
			log.debug(() -> "ConfigMap " + obj.getMetadata() + " was deleted.");
			onEvent(obj);
		}
	};

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
		this.monitorConfigMaps = properties.isMonitoringConfigMaps();
		this.enableReloadFiltering = properties.isEnableReloadFiltering();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "configmap");
	}

	@PostConstruct
	void inform() {
		if (monitorConfigMaps) {
			log.info(() -> "Kubernetes event-based configMap change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<V1ConfigMap> informer;
				String filter = null;

				if (enableReloadFiltering) {
					filter = ConfigReloadProperties.RELOAD_LABEL_FILTER + "=true";
					log.debug(() -> "added configmap informer for namespace : " + namespace + " with enabled filter");
				}
				else {
					log.debug(() -> "added configmap informer for namespace : " + namespace);
				}

				String filterOnInformerLabel = filter;
				informer = factory
						.sharedIndexInformerFor(
								(CallGeneratorParams params) -> coreV1Api.listNamespacedConfigMapCall(namespace, null,
										null, null, null, filterOnInformerLabel, null, params.resourceVersion, null,
										params.timeoutSeconds, params.watch, null),
								V1ConfigMap.class, V1ConfigMapList.class);
				informer.addEventHandler(handler);
				informers.add(informer);
			});

			factory.startAllRegisteredInformers();
		}
	}

	@PreDestroy
	void shutdown() {
		informers.forEach(SharedIndexInformer::stop);
		factory.stopAllRegisteredInformers();
	}

	protected void onEvent(V1ConfigMap configMap) {
		log.debug(() -> "onEvent configMap: " + configMap.toString());
		boolean changed = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
				findPropertySources(KubernetesClientConfigMapPropertySource.class));
		if (changed) {
			log.info(() ->"Configuration change detected, reloading properties.");
			reloadProperties();
		}
		else {
			log.warn(() ->"Configuration change was not detected.");
		}

	}

}
