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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.namespaces;


/**
 * An Event Based change detector that subscribes to changes in configMaps and fire a
 * reload when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class Fabric8EventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private final Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator;

	private final KubernetesClient kubernetesClient;

	private final boolean monitoringConfigMaps;

	private List<SharedIndexInformer<ConfigMap>> informers;

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final ResourceEventHandler<ConfigMap> handler = new ResourceEventHandler<>() {
		@Override
		public void onAdd(ConfigMap configMap) {
			onEvent(configMap);
		}

		@Override
		public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
			onEvent(newConfigMap);
		}

		@Override
		public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
			onEvent(configMap);
		}

		// leave as comment on purpose, may be this will be useful in the future
		// @Override
		// public void onNothing() {
		// boolean isStoreEmpty = informer.getStore().list().isEmpty();
		// if(!isStoreEmpty) {
		// // HTTP_GONE, thus re-inform
		// inform();
		// }
		// }
	}

	public Fabric8EventBasedConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		super(environment, properties, strategy);
		this.kubernetesClient = kubernetesClient;
		this.fabric8ConfigMapPropertySourceLocator = fabric8ConfigMapPropertySourceLocator;
		this.enableReloadFiltering = properties.isEnableReloadFiltering();
		monitoringConfigMaps = properties.isMonitoringConfigMaps();
		namespaces = namespaces(kubernetesClient, namespaceProvider, properties, "configmap");
	}

	@PostConstruct
	private void inform() {
		if (monitoringConfigMaps) {
			log.info("Kubernetes event-based configMap change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<ConfigMap> informer;
				if (enableReloadFiltering) {
					informer = kubernetesClient.configMaps().inNamespace(namespace)
						.withLabels(Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true")).inform();
					log.debug("added informer for namespace : " + namespace + " with enabled filter");
				}
				else {
					informer = kubernetesClient.configMaps().inNamespace(namespace).inform();
					log.debug("added informer for namespace : " + namespace);
				}

				informer.addEventHandler();
			});

			informer = kubernetesClient.configMaps().inform();
			informer.addEventHandler();
		}
	}

	@PreDestroy
	private void shutdown() {
		if (informers != null) {
			log.debug("closing configmap informer");
			informers.forEach(SharedInformer::close);
		}
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

	protected void onEvent(ConfigMap configMap) {
		log.debug("onEvent configMap: " + configMap.toString());
		boolean changed = changed(locateMapPropertySources(fabric8ConfigMapPropertySourceLocator, environment),
				findPropertySources(Fabric8ConfigMapPropertySource.class));
		if (changed) {
			log.info("Detected change in config maps");
			reloadProperties();
		}
	}

}
