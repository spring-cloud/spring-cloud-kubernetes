/*
 * Copyright 2013-present the original author or authors.
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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
import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.labelSelector;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedConfigMapChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientEventBasedConfigMapChangeDetector.class);

	private final CoreV1Api coreV1Api;

	private final KubernetesClientConfigMapPropertySourceLocator propertySourceLocator;

	private final ApiClient apiClient;

	private final List<SharedIndexInformer<V1ConfigMap>> informers = new ArrayList<>();

	private final List<SharedInformerFactory> factories = new ArrayList<>();

	private final Set<String> namespaces;

	private final ConfigurableEnvironment environment;

	private final boolean enableReloadFiltering;

	private final boolean monitoringConfigMaps;

	private final Map<String, String> configMapsLabels;

	// HA enabled for configuration watcher
	private final boolean haEnabled;

	// informers already running (skip starting more informers)
	private volatile boolean running;

	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		this(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider, false);
	}

	public KubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider, boolean haEnabled) {
		super(strategy);
		this.environment = environment;
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		this.monitoringConfigMaps = properties.monitoringConfigMaps();
		this.configMapsLabels = properties.configMapsLabels();
		this.haEnabled = haEnabled;
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "configmap");
	}

	@PostConstruct
	void inform() {
		// In HA mode, defer informer startup until this instance acquires leadership.
		// The leader callback restores the persisted state and then starts the informers.
		if (!haEnabled) {
			LOG.info(() -> "config watcher HA is disabled : starting configmap informers immediately");
			start(Map.of(), null);
		}
		else {
			LOG.info(() -> "config watcher HA is enabled : deferring configmap informer startup "
					+ "until leadership is acquired");
		}
	}

	public final void start(Map<String, String> storedResourceVersions,
			@Nullable Consumer<NamespaceAndResourceVersion> resourceVersionWriter) {
		if (running || !monitoringConfigMaps) {
			return;
		}
		InformerResourceVersionResolver resourceVersionResolver = new InformerResourceVersionResolver(
				storedResourceVersions, haEnabled);

		LOG.info(() -> "Kubernetes event-based configMap change detector activated");

		Map<String, String> labelSelector;

		if (enableReloadFiltering) {
			LOG.warn(() -> "enable reload filtering is deprecated and will be removed in the next major release");
			LOG.warn(() -> "use spring.cloud.kubernetes.reload.config-maps-labels instead");
			if (!configMapsLabels.isEmpty()) {
				LOG.warn(() -> "spring.cloud.kubernetes.reload.config-maps-labels is not empty, but "
						+ "spring.cloud.kubernetes.reload.enable-reload-filtering is enabled and will override the former");
			}
			labelSelector = Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true");
		}
		else {
			labelSelector = configMapsLabels;
		}

		ConfigMapResourceEventHandler handler = new ConfigMapResourceEventHandler(this::onEvent, resourceVersionWriter);

		namespaces.forEach(namespace -> {
			SharedIndexInformer<V1ConfigMap> informer;
			SharedInformerFactory factory = new SharedInformerFactory(apiClient);
			factories.add(factory);
			informer = factory.sharedIndexInformerFor((CallGeneratorParams params) -> {

				String resourceVersion = resourceVersionResolver.resolve(namespace, params.resourceVersion);
				var request = coreV1Api.listNamespacedConfigMap(namespace)
					.timeoutSeconds(params.timeoutSeconds)
					.resourceVersion(resourceVersion)
					.watch(params.watch)
					.labelSelector(labelSelector(labelSelector));

				// The stored resource version is the last checkpoint processed by the
				// previous leader. Restore the informer from exactly that snapshot so its
				// following WATCH requests start at the same version and can deliver
				// every change after the checkpoint.
				// we do not need haEnabled check here, but it short-circuits fast
				if (haEnabled && !params.watch && params.resourceVersion == null && resourceVersion != null) {
					request.resourceVersionMatch("Exact");
				}

				return request.buildCall(null);
			}, V1ConfigMap.class, V1ConfigMapList.class);

			LOG.debug(() -> "add configmap informer for namespace : " + namespace + " with labels : " + labelSelector);

			informer.addEventHandler(handler);
			informers.add(informer);
			factory.startAllRegisteredInformers();
		});
		running = true;

	}

	@PreDestroy
	void shutdown() {
		stop();
	}

	public final void stop() {
		if (!running) {
			return;
		}
		informers.forEach(SharedIndexInformer::stop);
		factories.forEach(SharedInformerFactory::stopAllRegisteredInformers);
		informers.clear();
		factories.clear();
		running = false;
	}

	protected void onEvent(KubernetesObject configMap) {
		boolean reload = ConfigReloadUtil.reload("config-map", configMap.toString(), propertySourceLocator, environment,
				KubernetesClientConfigMapPropertySource.class);
		if (reload) {
			reloadProperties();
		}

	}

}
