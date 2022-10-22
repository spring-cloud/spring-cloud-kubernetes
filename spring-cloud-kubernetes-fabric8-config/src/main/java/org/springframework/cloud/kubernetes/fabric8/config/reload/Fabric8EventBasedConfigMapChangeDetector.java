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

import java.util.ArrayList;
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
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.log.LogAccessor;

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

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8EventBasedConfigMapChangeDetector.class));

	private final Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator;

	private final KubernetesClient kubernetesClient;

	private final boolean monitoringConfigMaps;

	private final List<SharedIndexInformer<ConfigMap>> informers = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	public Fabric8EventBasedConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		super(environment, properties, strategy);
		this.kubernetesClient = kubernetesClient;
		this.fabric8ConfigMapPropertySourceLocator = fabric8ConfigMapPropertySourceLocator;
		this.enableReloadFiltering = properties.enableReloadFiltering();
		monitoringConfigMaps = properties.monitoringConfigMaps();
		namespaces = namespaces(kubernetesClient, namespaceProvider, properties, "configmap");
	}

	@PostConstruct
	private void inform() {
		if (monitoringConfigMaps) {
			LOG.info("Kubernetes event-based configMap change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<ConfigMap> informer;
				if (enableReloadFiltering) {
					informer = kubernetesClient.configMaps().inNamespace(namespace)
							.withLabels(Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true")).inform();
					LOG.debug("added configmap informer for namespace : " + namespace + " with enabled filter");
				}
				else {
					informer = kubernetesClient.configMaps().inNamespace(namespace).inform();
					LOG.debug("added configmap informer for namespace : " + namespace);
				}

				informer.addEventHandler(new ConfigMapInformerAwareEventHandler(informer));
				informers.add(informer);
			});
		}
	}

	@PreDestroy
	private void shutdown() {
		informers.forEach(SharedInformer::close);
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

	protected void onEvent(ConfigMap configMap) {
		boolean reload = ConfigReloadUtil.reload("config-map", configMap.toString(),
				fabric8ConfigMapPropertySourceLocator, environment, Fabric8ConfigMapPropertySource.class);
		if (reload) {
			reloadProperties();
		}
	}

	private final class ConfigMapInformerAwareEventHandler implements ResourceEventHandler<ConfigMap> {

		private final SharedIndexInformer<ConfigMap> informer;

		private ConfigMapInformerAwareEventHandler(SharedIndexInformer<ConfigMap> informer) {
			this.informer = informer;
		}

		@Override
		public void onAdd(ConfigMap configMap) {
			LOG.debug("ConfigMap " + configMap.getMetadata().getName() + " was added.");
			onEvent(configMap);
		}

		@Override
		public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
			LOG.debug("ConfigMap " + newConfigMap.getMetadata().getName() + " was updated.");
			onEvent(newConfigMap);
		}

		@Override
		public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
			LOG.debug("ConfigMap " + configMap.getMetadata().getName() + " was deleted.");
			onEvent(configMap);
		}

		@Override
		public void onNothing() {
			List<ConfigMap> store = informer.getStore().list();
			LOG.info("onNothing called with a store of size : " + store.size());
			LOG.info("this might be an indication of a HTTP_GONE code");
		}

	}

}
