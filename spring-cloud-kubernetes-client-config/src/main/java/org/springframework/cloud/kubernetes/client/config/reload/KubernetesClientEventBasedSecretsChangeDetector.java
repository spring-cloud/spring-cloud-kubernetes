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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;
import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.labelSelector;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedSecretsChangeDetector extends KubernetesClientEventBasedChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class));

	private final CoreV1Api coreV1Api;

	private final ApiClient apiClient;

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final boolean monitoringSecrets;

	private final Map<String, String> secretsLabels;

	private final KubernetesResourceEventHandler<V1Secret> handler = new KubernetesResourceEventHandler<>(
			(left, right) -> equals(left.getData(), right.getData()), this::onEvent);

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(strategy, propertySourceLocator, environment, KubernetesClientSecretsPropertySource.class);
		this.coreV1Api = coreV1Api;
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		this.monitoringSecrets = properties.monitoringSecrets();
		this.secretsLabels = properties.secretsLabels();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "secret");
	}

	@PostConstruct
	void inform() {
		LOG.info(() -> "Kubernetes event-based secrets change detector activated");

		Map<String, String> labelSelector = resolveLabelSelector(enableReloadFiltering, secretsLabels,
				"spring.cloud.kubernetes.reload.secrets-labels");

		if (monitoringSecrets) {
			namespaces.forEach(namespace -> {
				SharedIndexInformer<V1Secret> informer;

				SharedInformerFactory factory = new SharedInformerFactory(apiClient);
				factories.add(factory);
				informer = factory
					.sharedIndexInformerFor((CallGeneratorParams params) -> coreV1Api.listNamespacedSecret(namespace)
						.timeoutSeconds(params.timeoutSeconds)
						.resourceVersion(params.resourceVersion)
						.watch(params.watch)
						.labelSelector(labelSelector(labelSelector))
						.buildCall(null), V1Secret.class, V1SecretList.class);

				LOG.debug(() -> "secret informer for namespace : " + namespace + " with filter : " + secretsLabels);

				informer.addEventHandler(handler);
				informers.add(informer);
				factory.startAllRegisteredInformers();
			});
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
