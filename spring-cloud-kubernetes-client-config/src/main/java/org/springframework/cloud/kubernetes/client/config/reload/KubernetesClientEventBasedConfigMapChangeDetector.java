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
import java.util.Objects;
import java.util.Set;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientEventBasedConfigMapChangeDetector.class));

	private final CoreV1Api coreV1Api;

	private final KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	private final ApiClient apiClient;

	private final List<SharedIndexInformer<V1ConfigMap>> informers = new ArrayList<>();

	private final List<SharedInformerFactory> factories = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final ResourceEventHandler<V1ConfigMap> handler = new ResourceEventHandler<>() {

		@Override
		public void onAdd(V1ConfigMap configMap) {
			LOG.debug(() -> "ConfigMap " + configMap.getMetadata().getName() + " was added in namespace "
					+ configMap.getMetadata().getNamespace());
			onEvent(configMap);
		}

		@Override
		public void onUpdate(V1ConfigMap oldConfigMap, V1ConfigMap newConfigMap) {
			LOG.debug(() -> "ConfigMap " + newConfigMap.getMetadata().getName() + " was updated in namespace "
					+ newConfigMap.getMetadata().getNamespace());
			if (Objects.equals(oldConfigMap.getData(), newConfigMap.getData())) {
				LOG.debug(() -> "data in configmap has not changed, will not reload");
			}
			else {
				onEvent(newConfigMap);
			}
		}

		@Override
		public void onDelete(V1ConfigMap configMap, boolean deletedFinalStateUnknown) {
			LOG.debug(() -> "ConfigMap " + configMap.getMetadata().getName() + " was deleted in namespace "
					+ configMap.getMetadata().getNamespace());
			onEvent(configMap);
		}
	};

	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "configmap");
	}

	@PostConstruct
	void inform() {
		LOG.info(() -> "Kubernetes event-based configMap change detector activated");

		namespaces.forEach(namespace -> {
			SharedIndexInformer<V1ConfigMap> informer;
			String[] filter = new String[1];

			if (enableReloadFiltering) {
				filter[0] = ConfigReloadProperties.RELOAD_LABEL_FILTER + "=true";
			}
			SharedInformerFactory factory = new SharedInformerFactory(apiClient);
			factories.add(factory);
			informer = factory
					.sharedIndexInformerFor(
							(CallGeneratorParams params) -> coreV1Api.listNamespacedConfigMapCall(namespace, null, null,
									null, null, filter[0], null, params.resourceVersion, null, null,
									params.timeoutSeconds, params.watch, null),
							V1ConfigMap.class, V1ConfigMapList.class);

			LOG.debug(() -> "added configmap informer for namespace : " + namespace + " with filter : " + filter[0]);

			informer.addEventHandler(handler);
			informers.add(informer);
			factory.startAllRegisteredInformers();
		});

	}

	@PreDestroy
	void shutdown() {
		informers.forEach(SharedIndexInformer::stop);
		factories.forEach(SharedInformerFactory::stopAllRegisteredInformers);
	}

	protected void onEvent(KubernetesObject configMap) {
		boolean reload = ConfigReloadUtil.reload("config-map", configMap.toString(), propertySourceLocator, environment,
				KubernetesClientConfigMapPropertySource.class);
		if (reload) {
			reloadProperties();
		}

	}

}
