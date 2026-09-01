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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
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
public class KubernetesClientEventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class));

	private final CoreV1Api coreV1Api;

	private final KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

	private final ApiClient apiClient;

	private final List<SharedIndexInformer<V1Secret>> informers = new ArrayList<>();

	private final List<SharedInformerFactory> factories = new ArrayList<>();

	private final Set<String> namespaces;

	private final ConfigurableEnvironment environment;

	private final boolean enableReloadFiltering;

	private final boolean monitoringSecrets;

	private final Map<String, String> secretsLabels;

	// HA enabled for configuration watcher
	private final boolean haEnabled;

	// informers already running (skip starting more informers)
	private volatile boolean running;

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		this(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider, false);
	}

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider, boolean haEnabled) {
		super(strategy);
		this.environment = environment;
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		this.monitoringSecrets = properties.monitoringSecrets();
		this.secretsLabels = properties.secretsLabels();
		this.haEnabled = haEnabled;
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "secret");
	}

	@PostConstruct
	void inform() {
		// In HA mode, defer informer startup until this instance acquires leadership.
		// The leader callback restores the persisted state and then starts the informers.
		if (!haEnabled) {
			LOG.info(() -> "config watcher HA is disabled : starting secret informers immediately");
			start(Map.of(), null);
		}
		else {
			LOG.info(() -> "config watcher HA is enabled : deferring secret informer startup "
					+ "until leadership is acquired");
		}
	}

	public final void start(Map<String, String> storedResourceVersions,
			@Nullable Consumer<NamespaceAndResourceVersion> resourceVersionWriter) {
		if (running || !monitoringSecrets) {
			return;
		}
		InformerResourceVersionResolver resourceVersionResolver = new InformerResourceVersionResolver(
				storedResourceVersions, haEnabled);
		LOG.info(() -> "Kubernetes event-based secrets change detector activated");

		Map<String, String> labelSelector;

		if (enableReloadFiltering) {
			LOG.warn(() -> "enable reload filtering is deprecated and will be removed in the next major release");
			LOG.warn(() -> "use spring.cloud.kubernetes.reload.secrets-labels instead");
			if (!secretsLabels.isEmpty()) {
				LOG.warn(() -> "spring.cloud.kubernetes.reload.secrets-labels is not empty, but "
						+ "spring.cloud.kubernetes.reload.enable-reload-filtering is enabled and will override the former");
			}
			labelSelector = Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true");
		}
		else {
			labelSelector = secretsLabels;
		}

		SecretResourceEventHandler handler = new SecretResourceEventHandler(this::onEvent, resourceVersionWriter);
		namespaces.forEach(namespace -> {
			SharedIndexInformer<V1Secret> informer;
			SharedInformerFactory factory = new SharedInformerFactory(apiClient);
			factories.add(factory);
			informer = factory.sharedIndexInformerFor((CallGeneratorParams params) -> {

				String resourceVersion = resourceVersionResolver.resolve(namespace, params.resourceVersion);
				var request = coreV1Api.listNamespacedSecret(namespace)
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
			}, V1Secret.class, V1SecretList.class);

			LOG.debug(() -> "secret informer for namespace : " + namespace + " with filter : " + secretsLabels);

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

	protected void onEvent(KubernetesObject secret) {
		boolean reload = ConfigReloadUtil.reload("secrets", secret.toString(), propertySourceLocator, environment,
				KubernetesClientSecretsPropertySource.class);
		if (reload) {
			reloadProperties();
		}
	}

	static boolean equals(Map<String, byte[]> left, Map<String, byte[]> right) {
		Map<String, byte[]> innerLeft = Optional.ofNullable(left).orElse(Map.of());
		Map<String, byte[]> innerRight = Optional.ofNullable(right).orElse(Map.of());

		if (innerLeft.size() != innerRight.size()) {
			return false;
		}

		for (Map.Entry<String, byte[]> entry : innerLeft.entrySet()) {
			String key = entry.getKey();
			byte[] value = entry.getValue();
			if (!Arrays.equals(value, innerRight.get(key))) {
				return false;
			}
		}
		return true;
	}

}
