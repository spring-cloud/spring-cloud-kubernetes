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

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;
import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.labelSelector;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public abstract class KubernetesClientAbstractSecretsChangeDetector extends KubernetesClientEventBasedChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientAbstractSecretsChangeDetector.class);

	private final CoreV1Api coreV1Api;

	private final ApiClient apiClient;

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final boolean monitoringSecrets;

	private final Map<String, String> secretsLabels;

	public KubernetesClientAbstractSecretsChangeDetector(ConfigurationUpdateStrategy strategy,
			PropertySourceLocator propertySourceLocator, ConfigurableEnvironment environment, CoreV1Api coreV1Api,
			ConfigReloadProperties properties, KubernetesNamespaceProvider kubernetesNamespaceProvider,
			Class<? extends MapPropertySource> existingSourcesType) {
		super(strategy, propertySourceLocator, environment, existingSourcesType);

		this.coreV1Api = coreV1Api;
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		this.monitoringSecrets = properties.monitoringSecrets();
		this.secretsLabels = properties.secretsLabels();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "secret");
	}

	public final void start(Consumer<V1Secret> onEventHandler) {

		if (monitoringSecrets) {

			LOG.info(() -> "Kubernetes event-based secrets change detector activated");

			KubernetesResourceEventHandler<V1Secret> handler = new KubernetesResourceEventHandler<>(onEventHandler);

			Map<String, String> labelSelector = resolveLabelSelector(enableReloadFiltering, secretsLabels,
					"spring.cloud.kubernetes.reload.secrets-labels");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<V1Secret> informer;
				SharedInformerFactory factory = new SharedInformerFactory(apiClient);
				factories.add(factory);
				informer = factory.sharedIndexInformerFor((CallGeneratorParams params) -> {

					var request = coreV1Api.listNamespacedSecret(namespace)
						.timeoutSeconds(params.timeoutSeconds)
						.resourceVersion(params.resourceVersion)
						.watch(params.watch)
						.labelSelector(labelSelector(labelSelector));
					return request.buildCall(null);
				}, V1Secret.class, V1SecretList.class);
				LOG.debug(
						() -> "added secret informer for namespace : " + namespace + " with labels : " + labelSelector);

				informer.addEventHandler(handler);
				informers.add(informer);
				factory.startAllRegisteredInformers();
			});
		}

	}

}
